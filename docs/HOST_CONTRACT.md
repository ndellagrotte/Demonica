# Host contract

Demonica ships the Iris shader pipeline and GLSM as libraries, and hooks nothing
by itself. A host mod on 1.12.2/Cleanroom (Actinium is the reference host)
supplies the mixins, the terrain renderer, the services and the lifecycle calls.
This page lists what a host must provide, as of the syncline
`actinium@fee5de3834352050fc453bde1d93030c6d434983`. The "Actinium:" notes point
at the reference implementation in that commit.

## Artifacts

| Artifact | Contents |
|---|---|
| `com.demonica:demonica` | The Iris tree (`net.coderbot`, `kroppeb`, `net.irisshaders`) and its support classes; `assets/{iris,angelica,actinium}/**`; `META-INF/demonica_at.cfg` |
| `com.demonica:demonica-glsm` | GLSM (`com.gtnewhorizons.angelica.*`, `com.mitchej123.*`), the LWJGL 3 render backend and the `RenderBackend` service file, plus a minimal `net.minecraftforge.eventbus` shim |

- Their POMs bring in `org.taumc:glsl-transformation-lib`, `org.antlr:antlr4-runtime`
  and `org.anarres:jcpp` at runtime.
- **The jars are MCP-named and not remapped.** The host packages them into its
  own mod jar and remaps them with it.
- Neither jar carries mod metadata, mixin configs, an access-transformer
  attribute or FML manifest entries.

## Runtime dependencies the host supplies

- **GTNHLib and celeritas-common with the fee5de38 API.** These are Actinium's
  forks (`com.gtnewhorizon.gtnhlib`, `dhj.embeddedt.*`, `grondag.bitraster`).
  Demonica compiles against the copies in `vendor/` but never ships them.
- **LWJGL 3.4.1** (`lwjgl`, `lwjgl-opengl` and the natives) and Cleanroom's
  `lwjglx`.
- **joml 1.10.5**, plus fastutil, gson, ASM and log4j, which come from the
  Minecraft/Cleanroom environment.
- **Distant Horizons 3.3.0 is optional.** The DH compat code only runs when DH is
  present.

## Access transformers

Both jars were compiled against the 19 entries in `META-INF/demonica_at.cfg`, so
the host must apply the same entries at runtime. That includes the two
`public-f` entries, which drop `final` from
`AnimationMetadataSection.frameWidth`/`frameHeight`. The jar declares no `FMLAT`
attribute: copy the lines into the host's own AT.

## Services

| Service interface | Provided by |
|---|---|
| `com.gtnewhorizons.angelica.glsm.backend.RenderBackend` | demonica-glsm (`Lwjgl3GLRenderBackend`) |
| `com.mitchej123.glsm.GLStateManagerService` | the host (Actinium: `com.gtnewhorizons.angelica.client.rendering.AngelicaGLStateManagerService`) |
| `dhj.embeddedt.embeddium.impl.render.chunk.fog.FogService` | the host. Celeritas throws without one (Actinium: `com.dhj.actinium.render.terrain.fog.GLStateManagerFogService`) |

## GL redirector

GLSM only sees GL state when calls are rewritten to go through `GLStateManager`.
demonica-glsm provides the rewriter
(`com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector`), and the host
installs it as class transformers. Actinium's coremod registers `EarlyRedirectorTransformer`,
and `AngelicaLateTweaker` then replaces it with `AngelicaRedirectorTransformer`.

## Lifecycle calls, in order

1. `Iris.INSTANCE.onEarlyInitialize()` at `GameSettings.loadOptions` HEAD,
   once, when `Iris.enabled` is true.
2. At `OpenGlHelper.initializeTextures` RETURN:
   `GLStateManager.initialize(GLSMInitConfig)`, then `Iris.onRenderSystemInit()`
   on the main thread.
3. `Iris.onLoadingComplete()` in `Minecraft.init`, once, on the main thread.
   Actinium calls it before `SplashProgress.drawVanillaScreen`.
4. At FML initialization: `IrisGLSMBridge.register()` (host glue, see
   `StateUpdateNotifiers` below), then `Iris.INSTANCE.fmlInitEvent()`, then
   `MinecraftForge.EVENT_BUS.register(Iris.INSTANCE)`.
5. Every frame: `Iris.tryLoadShaderpackWhenPossible()`. Actinium calls it in
   `EntityRenderer.renderWorldPass`, after `ClippingHelperImpl.getInstance()`.

## Seams the host must fill

- **`WorldRendererCompatBridge.setProvider(...)`** gives Iris the host's terrain
  renderer, through which it draws shadow terrain (Actinium:
  `ActiniumWorldRenderer::instanceNullable`).
- **`IrisDebugOptions.setBridge(...)`** must be installed before `Iris` is
  class-initialized. `Iris.enabled` is `static final` and is read from the
  bridge's `enableIris()`.
- **`StateUpdateNotifiers`**: the alpha func/test, blend func, fog
  mode/start/end/density and color modulator notifiers must be wired to the
  host's GL state tracking (Actinium: `IrisGLSMBridge`).
- **Duck interfaces** the host mixes into Minecraft classes:
  - Iris: `IRenderTargetExt` on `Framebuffer`, `TextureAtlasExtension` on
    `TextureMap`, `TextureAtlasSpriteExtension` on `TextureAtlasSprite`,
    `IrisItemLightProvider` on `Item`
  - Celeritas: `ChunkTrackerHolder` on `WorldClient`, `ViewportProvider` on
    `Frustum`, `SimpleWorldRenderer.Provider` on `RenderGlobal`
- **`PostProcessingBridge`** (GTNHLib): the depth-texture provider (through
  `IRenderTargetExt`), the lightmap color and texture accessors, and the
  night-vision brightness invoker.
- **`ChunkTrackerHolder` / `ViewportProvider`**: the chunk tracker and frustum
  viewport that the shadow pass culls against.
- **`GLSMPerfDebugHooks`** (optional): stats providers, the configured-enabled
  flag and the enable-change listener.

## Terrain is host-owned

The main-pass terrain (Actinium overwrites `RenderGlobal.renderBlockLayer`), the
shadow terrain pass and every Iris mixin (Actinium: `mixins.actinium.iris.json`,
`features.iris.*`) are host code. Demonica provides the pipeline these hooks
drive.

## Resource namespaces

The tree loads resources from fixed namespaced paths. The host jar must keep
them intact:

- `assets/iris/**`: language files and GUI textures
- `assets/angelica/shaders/centerDepth.{vsh,fsh}`
- `assets/actinium/shaders/include/chunk_vertex.glsl`: the tree hardcodes the
  `actinium:` namespace

## Caveats

- **glsm refers to `com.dhj.actinium…` by name.** `GLSMDebug` reflects on
  `com.dhj.actinium.Actinium` for a debug option, and `GLSMPerfDebug` filters
  stack frames by Actinium package prefixes. Under another host these simply
  find nothing.
- **Split packages.** `com.gtnewhorizons.angelica.compat.mojang` is split across
  both jars (`NativeImage` is in demonica, the rest in demonica-glsm), and
  `com.gtnewhorizons.angelica.client.rendering` is shared with host classes.
  That is fine on the classpath, but not across JPMS modules.
- **The eventbus shim.** demonica-glsm ships its own minimal
  `net.minecraftforge.eventbus.api` (`bus.EventBus`, `event.MutableEvent`). An
  environment that also provides Forge's eventbus in that package would clash.
