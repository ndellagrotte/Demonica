# Forced Resourcepacks: a resource reload from preInit

## Symptom

With Forced Resourcepacks 1.1 applying a pack at launch ([#57](https://github.com/ndellagrotte/Demonica/issues/57),
Demonica 0.9.0 on Cleanroom 0.7.12): the loading screen was white, the dark frame around the Video Settings
list was gone (the whole screen was plain dirt), and entering a world ended the game without a crash report.
With the same pack enabled by hand instead, everything was fine. On the dev branch (after
[#56](https://github.com/ndellagrotte/Demonica/pull/56)) the same setup crashed at startup instead:
`NullPointerException: "VertexAttribState.current" is null` on the splash thread, then on the main thread, and
vanilla's "Caught error stitching, removing all assigned resourcepacks".

## Cause

The mod's `preInit` handler rewrites `gameSettings.resourcePacks` from `config/forcedresourcepacks.cfg` and calls
`Minecraft.scheduleResourcesRefresh()`. On the main thread that is a synchronous `Minecraft.refreshResources()`,
inside `FMLClientHandler.beginMinecraftLoading`: FML's splash thread owns the window context at that point, and
the main thread sits on Cleanroom's hidden `SharedGLContext`, which shares objects with the window but has its
own bindings, vertex arrays and framebuffers, and hands out the same numbers for them.

That reload is the third one Cleanroom's Kirino `McFontReloadListener` sees. It skips the first two (its own
registration and FML's reload after `loadMods`), so this one rebuilds the McTTF text runtime: the old
`SimpleTextRuntime` is closed (its glyph atlas, buffers and VAO deleted) and a new one built, on the shared
context, through GLSM. The new VAO is the first on that context, so its name is 1: the number of the window
context's default VAO, the one the splash thread draws its text with.

GLSM keeps one cache, describing the window context. Since #56 the main thread is "not caching" while the splash
thread holds the window, but several entry points wrote the cache anyway:

- `glGenVertexArrays` forgot the record of the new id (meant for driver-recycled ids, #150). Here the id was the
  splash thread's live VAO: its attribute record went back to the pool and `VertexAttribState.current` was
  nulled mid-draw, the NPE above. `glBindVertexArray` mapped 0 to the default VAO (an invalid name on the shared
  context) and recorded the bind; `glDeleteVertexArrays`, `glBindBuffer`, `glBindFramebuffer` (which also skipped
  a real bind when the cache matched), `glDeleteFramebuffers`, the vertex-attribute pointer and enable calls,
  `glClientActiveTexture` and `onDeleteTexture` recorded the shared context's state as the window's.
- `deferDeleteTexture` read the splash thread's cached unit and binding, and bound them on the main thread's
  context.
- The deferred-delete set and `TextureTargets` were plain fastutil structures touched by both threads at once.
- `markSplashComplete` set a "re-seed the driver next frame" flag that nothing consumed, so after the splash the
  cache was never pushed into the window context.

With those writes gated the client got past loading, and then showed the reporter's screenshot: Video Settings
with no dark frame. After the splash, `refreshMcFontIfNeeded` (packs are present) closes the runtime built at
preInit before building another, and that close deletes VAO 1 on the window context, where 1 is GLSM's default
VAO: the FFP emulation's vertex arrays were gone, and the dark quads with them. The off-screen context is
destroyed at splash finish, so Kirino's own object was already dead.

On 0.9.0, which predates #56, the main thread was still the context "holder" during the whole splash, so the
entire preInit rebuild (and vanilla's own loading) was cached as window state while the splash thread ran
uncached: stale texture and VAO bookkeeping after the handover, which is what the reporter's Video Settings
screen and world-join crash look like. The white loading screen was the #56 bug, independent of the mod.

## Fix

- Every GLSM cache write reachable from a non-caching thread checks `isCachingEnabled()` first and is a plain
  passthrough otherwise: `glGenVertexArrays` keeps the aliased record (and logs
  `VAO N generated off the window context aliases a cached window-context VAO` once), `glBindVertexArray` binds
  the name it was given, and the buffer, framebuffer, vertex-attribute, client-texture and delete paths record
  nothing. `deferDeleteTexture` off the window context shrinks through the driver's real unit-0 binding.
- `glGenVertexArrays` off the window context remembers the name (`offContextVertexArrays`); the first delete of
  that name on the window context is skipped with a one-time warning, since the only object the caller could
  mean died with the off-screen context and the name now belongs to a window-context VAO.
- `TextureTargets` owns the deferred-delete set and is synchronized.
- `markSplashComplete` calls `replayStateToBackend` itself (on the main thread, window context current, VAO/VBO/EBO
  freshly reset by `MixinSplashProgress.finish`), and the replay now also binds the cached 2D texture of every
  unit the cache knows, dropping names the driver no longer has. `RenderBackend.onFrameBegin` and the pending
  flag are gone.
- `GLStateManagerSplashGateContractTest` pins the gates and the replay in the class bytes;
  `TextureTargetsTest` covers the deferred set and hammers it from two threads.

## Repro

```
./gradlew runClient -PwithForcedResourcePacks -PdevScript=@scripts/forced-packs.txt \
    -PdevProps=demonica.debug.splashDump=5,demonica.debug.textureDeletes=true -Dorg.gradle.jvmargs=-Xmx2G
```

`-PwithForcedResourcePacks` loads Forced Resourcepacks 1.1 (CurseForge 997732, file 5241646). The mod reads
`run/client/config/forcedresourcepacks.cfg`; set it to force the dev client's `demonica-custom-font` folder pack
(category `general` > `configdefault`, `S:defaultPacks < demonica-custom-font >`). The script
(`scripts/forced-packs.txt`, copied into `run/client/scripts`) shoots the main menu, opens Video Settings
(`expect-screen CeleritasVideoOptionsScreen`, shot with the HUD), then a world at a fixed spectator view
(seed 1234567, the profile script's camera). Run it without the flag for the reference frames.

Before the fix the client never reached the script: the preInit reload logged
`Starts reloading McTTF font runtime`, then `Splash thread Exception ... VertexAttribState.current is null`, the
main thread hit the same NPE in `VertexAttribState.set` from Kirino's `VAO.<init>`, and the game crashed with
`IllegalStateException: Splash thread`. After the fix the same run logs the reload, the VAO-alias warning for
VAO 1, Kirino's `Refreshed McTTF font` after the splash, and completes all three shots with no GL error; the
Video Settings frame matches the run without the mod except for the hovered row, and the loading screen frames
(`screenshots/splash-<n>.png`) are textured.

Production check (Prism instance `forced-packs-smoke`, a clone of the 0.9.0 release smoke instance with the mod,
`demonica-custom-font.zip` forced, the same script): the 0.9.0 jars show the reporter's screenshot, Video Settings
with no dark frame, and reach the world without crashing on Linux/NVIDIA (the reporter's silent exit at world
join is what drawing through a deleted default VAO looks like on another driver). The fixed jars draw the frame.
