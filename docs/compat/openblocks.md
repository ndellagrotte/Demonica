# OpenBlocks Reopened: a shader validated from preInit, on the splash's shared context

## Symptom

With OpenBlocks Reopened 1.8.4 on macOS ([#42](https://github.com/ndellagrotte/Demonica/issues/42), Demonica
0.8.1 on Cleanroom 0.7.6, Apple silicon): the game crashes during mod loading with

```
org.lwjgl.opengl.OpenGLException: Shader validate error: Validation Failed: No vertex array object bound.
    at openmods.renderer.shaders.ShaderProgramBuilder.build(ShaderProgramBuilder.java:61)
    at openblocks.client.renderer.skyblock.SkyBlockRenderer.onResourceManagerReload(SkyBlockRenderer.java:48)
    at net.minecraft.client.resources.SimpleReloadableResourceManager.registerReloadListener(...)
    at openblocks.client.ClientProxy.preInit(ClientProxy.java:174)
```

Without Demonica the game starts. OpenBlocks 1.8.3.1 and earlier had no Sky Block renderer and were fine.

## Cause

OpenBlocks 1.8.4 ([1f0a9c60](https://github.com/ACGaming/OpenBlocksReopened/commit/1f0a9c6069e2f5168ef72dd74789d6d80f6a5c9d))
draws its Sky Block through a custom `BlockRenderLayer` and a GLSL 1.20 program. The program is compiled when the
renderer registers itself as a reload listener, which `SimpleReloadableResourceManager.registerReloadListener`
runs at once: in the mod's `preInit`, on the main thread, through `glCreateProgram`, `glLinkProgram` and
`glValidateProgram`, and the builder throws when `GL_VALIDATE_STATUS` is false.

Three things meet there:

- On macOS Demonica resolves its `AUTO` profile to a 4.1 core context (`OpenGlProfile.forPlatform`,
  `MixinMinecraftDisplay`). Without Demonica, Cleanroom's own request falls through its version list to a legacy
  2.1 context on macOS, where a program validates with no vertex array bound. Apple's driver is the one that
  reports `No vertex array object bound` from `glValidateProgram` on a core context; Mesa does not check it, so
  Linux never shows the message.
- From `SplashProgress.start` to `finish` the main thread sits on Cleanroom's hidden `SharedGLContext` while the
  splash thread owns the window ([forced-resourcepacks.md](forced-resourcepacks.md) describes the same setup for
  #57). The shared context is created with the SDL attributes the window request left behind, so it is a core
  context too. Every mod `preInit` runs on it.
- GLSM's default VAO, the one it binds for VAO 0 on a core profile, is created on the window context in
  `GLStateManager.init`. Vertex arrays are not shared between contexts, and nothing created or bound one on the
  shared context: GLSM is a passthrough there since #57, and `glValidateProgram` goes straight to the driver.

So on a core profile any legacy mod that validates a program, sets vertex-attribute pointers or draws from
preInit does it with VAO 0 bound. The v0.8.1 code has the same gap; the splash-context tracking of #56 and #57
changed what GLSM recorded, not what was bound.

## Fix

- `GLStateManager.onSharedContextCurrent` (called from `MixinSplashProgress` right after Cleanroom's
  `SharedGLContext.makeCurrent()` in `SplashProgress.start`) creates a VAO on the shared context and binds it,
  on a core profile only; `glBindVertexArray(0)` on that context maps to it, as 0 maps to the default VAO on the
  window. The binding is per-context state, so the `pause`/`resume` swaps need nothing. The VAO is never deleted:
  `SplashProgress.finish` closes the context, and `markSplashComplete` forgets the name.
  `GLStateManagerSplashGateContractTest` pins the hook, the mapping and the forget.
- Splash replacements that do not go through `SharedGLContext` (Modern Splash, #150) are not covered.

The Sky Block itself does not render under Celeritas, with or without Demonica (the reporter's "disabled"
look): Celeritas draws every chunk layer with its own shader program, so the program OpenBlocks binds before
`renderBlockLayer(customLayer)` is overridden, and vanilla's `RegionRenderCacheBuilder`, which OpenBlocks
patches for its extra layer, is never used. OpenBlocks ships a Nothirium backend for this and has none for
Celeritas; that is not something GLSM can fix.

## Repro

```
./gradlew runClient -PwithOpenBlocks -PdevScript=@scripts/openblocks.txt -Dorg.gradle.jvmargs=-Xmx2G
./gradlew runClient -PwithOpenBlocks -PdevScript=@scripts/openblocks.txt -PdevProps=demonica.openglProfile=core -Dorg.gradle.jvmargs=-Xmx2G
```

`-PwithOpenBlocks` loads OpenBlocks Reopened 1.8.4 (CurseForge 1342654, file 9106076; OpenModsLib is nested in
it). The script (`scripts/openblocks.txt`, copied into `run/client/scripts`) shoots the main menu and a world at a
fixed spectator view. OpenBlocks logs `Sky block renderer activated using minecraft backend` from preInit, and
Celeritas `Falling back to cutout-like behavior for custom block render layer 'Sky Block'` when it builds its
passes.

Linux cannot show the macOS message: neither Mesa nor NVIDIA fail `glValidateProgram` over a missing vertex
array. What the core run shows instead (2026-10-29, dev at `b670b5d5`, NVIDIA 615, splash on) is the mechanism:
`Shared off-screen context on a core profile: default VAO 1 created and bound for the splash (#42)` before the
first mod `preInit`, and a temporary probe in `glValidateProgram` logged `GL_VERTEX_ARRAY_BINDING=1` with
`caching=false` for both of OpenBlocks' validations on the client thread, where the unfixed code had 0 bound.
Both profiles reach the world with the two shots and no GL error beyond the core profile's usual
`Post startup 1280` line, which the same run on the unfixed code logs too. The #57 scene
(`-PwithForcedResourcePacks` with `scripts/forced-packs.txt`) on the core profile still passes: Kirino's preInit
VAO is now name 2 (the shared context's default VAO took 1), kept and later skipped on delete as before, and the
Video Settings frame is drawn. The reporter's macOS run is the only check of the message itself.
