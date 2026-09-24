# Runtime spike: upstream Celeritas in Demonica's dev environment

Phase 0 of the plan to make Demonica a shader mod on upstream Celeritas starts
by testing the riskiest assumptions before any code is ported. The spike ran on
the throwaway branch `spike/celeritas-runtime` (commit `eed38ca1`) on
2026-09-23. This page records what it proved and the rules it produced.

## Environment

| | |
|---|---|
| Loader | Cleanroom 0.6.12-alpha (CleanMix 0.7.2 = Mixin 0.8.7, Foundation 0.19.11), Unimined 1.4.36-kappa dev run |
| Java | OpenJDK 25.0.4.1 |
| GPU | NVIDIA GeForce RTX 3080, driver 615.71.09, OpenGL 4.6 **compatibility** context (Cleanroom's default display) |
| Celeritas | the pin in [`PIN.md`](PIN.md), remapped SRG → MCP by Unimined |
| Driver | `com.demonica.dev.DevHarness` with a script: seed 1234567, spectator, `/time set 6000` and `13000`, `/tp` for the views, a screenshot after terrain finishes building |

## 1. Celeritas alone

**Passed.**
- Cleanroom finds Celeritas's coremod in the remapped jar on `-Dcrl.dev.extrapath`
  (`Instantiating coremod class CeleritasLoadingPlugin`), and the mod container is
  `celeritas 2.4.0-dev`.
- **All 27 upstream mixins apply** (counted from CleanMix's `Mixing … from
  mixins.celeritas.json` lines), with no injection errors, no `Skipping method`
  and no critical mixin failures.
- `ModAccessTransformer` loads the jar's AT: 10 rules, already in MCP names
  because Unimined remaps `FMLAT` files. No entries need to move into
  Demonica's AT for dev.
- `RenderGlobal` implements `SimpleWorldRenderer$Provider`, and
  `celeritas$getWorldRenderer()` returns a `CeleritasWorldRenderer`.
- Terrain, sky, clouds and distance fog render normally, in day and dusk views.
  Upstream's fog is spherical (`getFogShapeIndex() == 0`).

## 2. A minimal GLSM coremod

**Passed**, with one class-loading rule added (section 3). The spike coremod
had 12 files, plus the throwaway mod that armed the harness:
- `SpikeCoremod`, an `IFMLLoadingPlugin` and `IEarlyMixinLoader` that queues
  `AngelicaLateTweaker`;
- the redirector (`AngelicaLateTweaker`, `AngelicaRedirectorTransformer`,
  `EarlyRedirectorTransformer`, `AngelicaRedirector`, `AngelicaClassDump`);
- `AngelicaGLStateManagerService`;
- three mixins:
  - GLSM initialisation at `OpenGlHelper.initializeTextures` RETURN;
  - GLSM's splash bookkeeping on `SplashProgress.start`/`finish`;
  - **S15**: HEAD-cancel injections on upstream's `GLStateManagerFogService`
    that return GLSM's fog state (Actinium's service, including its planar fog
    shape).

Results:
- GLSM initialises (`GL line width: … accepted`), and the late redirector
  registers with Foundation after mixin setup.
- **No `NoSuchMethodError`, no `LinkageError`, no GL errors** in the log across
  world load, terrain building, a time change and shutdown. Before the spike,
  `GlsmRedirectLinkageTest` had found the three missing `GLStateManager`
  overloads (`glGenTextures([I)V`, `glDeleteTextures([I)V`, `glDrawBuffers([I)V`),
  which are now added.
- **Terrain renders through GLSM with correct fog.** It matches the Celeritas-only
  frames except at the screen edges, where planar fog (Actinium's choice, and
  vanilla 1.12's eye-plane fog) is lighter than upstream's spherical fog.
  Vanilla clouds, drawn by GLSM's fixed-function emulation, come out slightly
  more opaque than on the real fixed-function path. That belongs to GLSM, not
  to the seam.
- **S15 is required even with shaders off.** The same run without S15 draws
  every terrain section in solid fog colour. Upstream reads vanilla's
  `GlStateManager.fogState`, which never changes once the redirector sends
  vanilla's fog calls to GLSM. This run also proves that the redirector is live
  for vanilla's classes. `GlsmRedirectLinkageTest` shows it also rewrites
  Celeritas's `LWJGL3Service` (96 `GL*C` call sites, plus ARB, EXT and KHR entry
  points), and that every call it rewrites in the jar now links.

The spike used Cleanroom's compatibility context, not Actinium's core-profile
display (`MixinMinecraftCoreProfileDisplay` with the Tessellator and
vertex-buffer mixins). The core-profile path is ported in Phase 3/4 and checked
at Checkpoint 3.

## 3. Dev class loading

- **Mod code must not be on the JavaExec classpath.** Demonica's class and
  resource directories reach Cleanroom only through `-Dcrl.dev.extrapath`, which
  is Actinium's rule. Unimined moves directories there itself only when they
  already exist at configuration time, so the build does it explicitly.
- **New: GLSM and GTNHLib must travel the same way.** Left on the app classpath,
  the `:glsm` jar was loaded by the app class loader while the transformer
  calling it ran in `LaunchClassLoader`. The first redirect then failed with
  `LinkageError: loader constraint violation … different Class objects for the
  type org/objectweb/asm/tree/ClassNode`. In production both are merged into the
  mod jar, so in dev their jars go on the extrapath next to the mod classes.
- **`runClient` must depend on `classes`** and the `:glsm`/`:GTNHLib` jars.
  Unimined's `preRunClient` does not, so a run can start with stale classes.
- `-Dmixin.env.ignoreConstraints` is not needed.
- Only the client run is enabled. Celeritas's coremod registers client-only
  mixin targets, and `runServer` is not used.
- CleanMix logs `Unable to locate resource for mixin config source … Mixin config
  does not reside in a jar file` for configs loaded from a directory. It is a
  warning, and harmless in dev.

## 4. Seam anchors

`AnchorInventoryTest` checks every anchor the plan needs (S1–S19, I1, I2)
against the pinned jar, along with upstream's overwrite set (10 members) and
its priorities (RenderGlobal 1000, BiomeColorHelper 1200, TextureUtil 900). All
of them are present.

## What the later phases take from this

- Phase 3 builds on sections 2 and 3: extrapath for every piece merged into the
  mod jar, run dependencies, S15 in the quarantine skeleton, and GLSM init and
  splash handling in the core config.
- The harness steps used here are the checkpoint driver. Screenshots go to
  `run/client/screenshots/`. The logs to scan are `run/client/logs/latest.log`
  and `debug.log`.
