# Demonica scope research

Date: 2026-09-25. Tree: `dev` at `11b6c61` (0.3.0-SNAPSHOT, the day after 0.2.0).

This page is a survey of what Demonica is made of today, where its maintenance
cost comes from, and what could be cut, replaced or modernised next. It was
written against this tree and against the upstream trees at their heads on the
same day: Angelica `a23cefba`, Actinium `16fee8fc`, upstream Celeritas
`7e5e9d7e` (`stonecutter`), S8TNLib `v0.2.0`, Unimined `1.4.2`. Everything is
static analysis: line counts, tree diffs and greps. The research sandbox could
not finish a Gradle build (Maven Central rate-limited its proxy on every
attempt), so nothing here was compiled or run in game. Where a claim depends on
runtime behaviour, it says so. Appendix A lists how each number was produced.

## 1. Summary

Demonica already made the two cuts that matter most: the terrain renderer is
upstream Celeritas, installed separately, and GTNHLib lives in S8TNLib. What
ships now is about 136,000 lines of Java, and the Iris pipeline is under 40% of
it. The rest arrived with the pipeline, from Angelica through Actinium: a GL
state manager with a bytecode redirector and a fixed-function emulator
(38,000 lines), Reese's Sodium Options with a copy of Mojang's modern GUI API
(10,600), Actinium's draw fast paths (6,100), diagnostics that ship in the jar
(7,200), compatibility code for 29 other mods (3,600 plus 15 mixin configs), and
the machinery that keeps 21 patches on Celeritas's internals from crashing the
game (4,000 plus a 419-line ledger that is also a test input).

The ten findings, by leverage:

1. **The Celeritas seam is the most fragile thing in the repository, and the
   drafts that would retire most of it have not been sent.** 21 quarantine
   mixins (23 ledger entries) target 16 Celeritas classes. In the five days
   since the pin, upstream touched 6 of those 16, removed the fog-service SPI
   that patch S15 sits on, and changed two constructor signatures. One provider
   interface in Celeritas `common`, already drafted in
   `docs/celeritas/patches/README.md`, would retire 10 of the 23 ledger entries
   outright, most of an eleventh, and most of the guard.
2. **The guard is more than Iris itself does.** Iris refuses to run on a Sodium
   version it was not built for. Demonica instead audits 316 bytecode anchors at
   startup and degrades in four levels. That is 2,000 lines of guard, 1,000
   lines of tests, a build step, a snapshot of upstream's mixin inventory and a
   five-step manual pin procedure. A plain version gate would do what Iris does.
3. **GLSM, the redirector and the core-profile display are the largest block and
   the least related to shaders.** They exist so that 1.12.2 runs on an OpenGL
   core profile with fixed-function emulation. Most mod compatibility code is
   fallout from them. This is the one architectural decision that should be made
   deliberately (section 4.4); everything else is trimming.
4. **Actinium's performance features are a second product inside the jar.**
   Deferred draw batching, the batching font renderer, fast lit items, streaming
   uploaders, the panorama, window modes: 6,100 lines, all behind options, none
   needed by a shader pack, and five compat entries exist only for them.
   *Done on `feat/drop-perf-features` (0.3.0-SNAPSHOT), with corrections: see
   3.3.*
5. **Reese's Sodium Options can go.** 10,620 lines including 24 files that
   re-create Mojang's 1.20 GUI API. The ledger records that Celeritas's own
   Video Settings screen lists Demonica's settings when RSO is off.
6. **Two of the shipped class transformers are never installed.** Actinium's
   coremod registers the StellarCore and Gnetum HUD-caching transformers;
   Demonica's does not, so `GLSMConfig.hudCacheOverride` never becomes true and
   the VoxelMap and Revo UI branches that read it are dead. Either wire them in
   or delete them and their two test-only mod pins.
7. **About 3,000 lines are dead or vestigial**: 35 classes nothing references
   (1,626 lines, two of them reached only by tests), the `com.mitchej123.glsm`
   service layer whose loader nothing calls (about 1,000 lines) even though
   `verifyDistributedJar` requires its service file, and the two transformers
   above.
8. **7,200 lines of diagnostics ship in the mod jar**, including a 2,789-line GL
   debug facility, a 1,667-line GL flight recorder and a scripted dev harness
   that `Demonica.onInit` installs in production.
9. **S8TNLib's split costs more than it returns while Demonica is its only
   host.** CI checks out and builds it on every run, contributors publish it to
   `mavenLocal` by hand, and two verification tasks exist only because of that.
   Its `bytebuf` package, 55% of the library, backports LWJGL 3 memory
   utilities that Cleanroom's LWJGL 3 already provides.
10. **The toolchain is current but has avoidable dependencies**: a Unimined fork
    on a third-party Maven although upstream Unimined 1.4.2 lists Cleanroom
    support and the same DSL calls; Lombok in 65 files; a Jabel stub; mixin
    configs declared at Java 8; unfiltered repositories that let a Maven
    Central outage break Cleanroom resolution (seen in this sandbox).

## 2. Where the project stands

### 2.1 Timeline

| Date | Event |
|---|---|
| 2026-04-14 | Angelica baseline `9fd02900` (Angelica PR #1626); the repository's git origin |
| 2026-09-23 | Sync era: the Iris tree, GLSM, GTNHLib and celeritas-common synced verbatim from Actinium `fee5de38`, then `4a19c959` (the fork point); the runtime spike on upstream Celeritas |
| 2026-09-23 to 09-24 | Demonica becomes a mod on upstream Celeritas: quarantine, guard, Actinium's features and compat ported |
| 2026-09-24 | 0.1.0: GTNHLib comes from S8TNLib 0.1.0; GPL-3.0 settled |
| 2026-09-25 | 0.2.0: S8TNLib 0.2.0, per-buffer blend directives |

Demonica as a mod is three days old, with 59 commits on those three days and a
single author. Its documentation is unusually complete (34 pages, 2,488 lines),
and some of it is load-bearing: `QuarantineLedgerTest` parses the ledger's
markdown table, and the build declares `docs/celeritas/LEDGER.md` and
`docs/celeritas/patches/` as test inputs.

### 2.2 Inventory

| Tree | Files | Lines | Origin |
|---|---|---|---|
| `src/main/java` (the mod) | 343 | 34,455 | Actinium's root project (`com.dhj.actinium` became `com.demonica`), Angelica glue (`com.gtnewhorizons.angelica`), RSO (`me.flashyreese`) |
| `glsm/` + `src/lwjglCommon` + `src/lwjgl3` | 223 | 37,958 | Angelica's GLSM as Actinium adapted it, plus the mitchej123 service layer |
| `shader/` | 506 | 53,206 | Iris via Angelica's backport via Actinium; kroppeb's expression parser; Demonica's seam types |
| S8TNLib (merged into the jar) | 58 | 10,389 | GTNHLib via Actinium |
| **Shipped total** | **1,130** | **136,008** | |
| `src/test/java` | 147 | 16,061 (625 tests) | Actinium's ported tests plus Demonica's |
| `docs/` | 34 | 2,488 | |

111 mixin classes across 20 configs: `iris` 38, `celeritas` 21, `core` 20,
and 32 spread over 15 per-mod configs. The build resolves 29 distinct third-party
mods from CurseMaven and Modrinth (34 CurseMaven and 5 Modrinth coordinates) to
compile the compat code and the tests.

### 2.3 What the jar is made of, by purpose

Approximate, by package; some classes straddle two rows.

| Purpose | Where | Lines | Needed for "Iris on Celeritas"? |
|---|---|---|---|
| Iris pipeline: pack loading, transforms, uniforms, render targets, GUI, DH integration | `shader/` minus debug and seam | ~45,800 | Yes. This is the product |
| Iris hooks into vanilla rendering | `mixin/features` (38 mixins) | 3,899 | Yes |
| Celeritas seam: 21 quarantine mixins, guard, terrain glue, seam types | root `mixin/celeritas`, `celeritas/`; shader `com/demonica/celeritas`, `net/coderbot/iris/celeritas` | ~6,400 | Yes today; most of it is replaceable by an upstream API (3.1) |
| GLSM: state tracking, redirector, FFP shader generation, display-list recording, streaming, LWJGL abstraction, Iris bridge | `glsm/`, lwjgl trees, `IrisGLSMBridge`, `AngelicaGLStateManagerService`, redirector transformers | ~39,000 | Partly. Iris needs the state tracking and the bridge; the FFP emulator, display lists, streaming drawer and most of the redirector serve the core profile (3.2) |
| Core-profile display and what it forces | `MixinMinecraftCoreProfileDisplay`, `CoreProfileContextAttributes`, `MacDisplayForwardCompatTransformer`, the end-portal replacement renderer, projective texcoords | ~2,400 | No. A platform choice; macOS is the argument for it |
| Actinium's performance features | `render/*`, `DeferredDrawBatcher`, `BatchingFontRenderer`, `PanoramaRenderer`, `FastLitItemDisplayListCache`, window modes, their mixins | ~6,600 | No (3.3) |
| Reese's Sodium Options | `me/flashyreese`, `gui/rso/compat` | 10,620 | No (3.4) |
| Compatibility with other mods | `mixin/mod`, `mixin/early`, `compat/`, `loading/fml` | 3,612 | About five entries; the rest is fallout (3.5) |
| Diagnostics and dev tooling | `net/coderbot/iris/debug`, GLSM `debug`, root `debug` and `dev` | ~7,200 | No, and not in the shipped jar (3.6) |
| Options, config, lifecycle, bridges | `config/`, `gui/` minus RSO, `Demonica`, `loading/`, `runtime/`, `mixins/` | ~2,300 | Yes |
| S8TNLib | merged | 10,389 | Partly: tessellator capture, VAO/VBO helpers and vertex formats used by GLSM and the fast paths (3.7) |

Read the other way: the Iris pipeline plus its vanilla hooks plus the seam is
about 56,000 lines, 41% of what ships.

### 2.4 The build and CI as they are

- Gradle 9.7.1, Unimined `1.4.36-kappa` (a fork resolved from
  `maven.arcseekers.com`), JDK 25 toolchain compiling with `--release 21`,
  JUnit 6.0.3, Lombok 1.18.46, GitHub Actions at current major versions. All
  current.
- `org.gradle.parallel` and the configuration cache are off because Unimined is
  not configuration-cache safe; subprojects resolve the root's compile
  classpath.
- Three projects (`:`, `:glsm`, `:shader`) are merged into one jar by
  `mergeEmbeddedLibraryClasses`, with S8TNLib's jar unpacked into the same
  output. `verifyModuleBoundaries`, `verifyRunClasspath`, `verifyS8tnlibPin`,
  `verifyCeleritasPin`, `verifyDistributedJar` and `verifyProductionAnchors`
  hang off `check`; `check` therefore needs the SRG remap.
- CI checks out S8TNLib at the pinned tag and publishes it to `mavenLocal`
  before every Demonica build. Release jars are uploaded as workflow artifacts;
  attaching them to a GitHub release is manual.
- The Celeritas dependency is an unofficial auto-build of an untagged upstream
  commit (upstream publishes no binaries), pinned by SHA-256, resolved from
  `maven.outlands.top`.
- Repositories other than Celeritas's and `mavenLocal` are unfiltered. In this
  sandbox, S8TNLib's build asked Maven Central for `com.cleanroommc:cleanroom`
  first, got a 429, and Gradle disabled the repository for the build.

### 2.5 The upstreams and how fast they move

| Upstream | Demonica's base | Head on 2026-09-25 | Movement since the base |
|---|---|---|---|
| Angelica (Iris backport, GLSM) | `9fd02900`, 2026-04-14, PR #1626 | `a23cefba`, 2026-09-24, PR #2130 | Five months and about 500 issue and PR numbers. Measured below |
| Actinium (the 1.12.2 adaptation Demonica forked) | `4a19c959`, 2026-09-23 | `16fee8fc`, 2026-09-25, PR #190 | 35 files, +1,346/-109 in two days; still vendors its Celeritas fork (313 files) |
| Celeritas (runtime dependency) | `06999aab`, committed 2026-09-20, auto-built 2026-09-22 | `7e5e9d7e`, 2026-09-25 | 4 commits, 20 files, +121/-93; 6 of the 16 quarantine target classes changed |
| Cleanroom | loader 0.6.12-alpha in the build; 0.6.12 and 0.6.13 tested | 0.6.13-alpha | |
| S8TNLib | v0.2.0 | v0.2.0 | Demonica is its only host |
| Unimined | 1.4.36-kappa | kappa 1.4.43; upstream 1.4.2 (2026-09-16) with a `CleanroomPatcher` and a Cleanroom 1.12.2 integration test | |

Measured drift of Demonica's trees (whitespace-insensitive unified diff over
files present on both sides):

| Comparison | Identical | Differ | Only Demonica | Only upstream | Changed lines in differing files |
|---|---|---|---|---|---|
| GLSM vs Angelica head | 37 | 110 | 75 | 52 | 16,802 (`GLStateManager.java` alone 5,112) |
| GLSM vs Actinium head | 209 | 13 | 0 | 0 | 144 |
| Iris tree vs Angelica head | 226 | 195 | 63 | 37 | 13,811 |
| Iris tree vs Actinium head | 444 | 45 | 17 | 0 | 403 |
| Root shared code (`com.gtnewhorizons`, `me.flashyreese`, `net.irisshaders`) vs Actinium head | 35 | 50 | 5 | 14 | not measured |

Two things follow. Demonica's `shader/` and `glsm/` are still Actinium's trees
to within a few hundred lines, so Actinium's future syncs from Angelica are
cheap to take as long as that stays true. And Angelica's Iris tree has moved on
in ways Demonica lacks: the 37 upstream-only files include a pre-raster compute
dispatcher, read-write image store extraction, sampler alias deduplication, a
parity framebuffer, a uniform manifest, shader search, pack download entries,
an end-flash uniform and a shadow graph gate. Actinium tracks these through its
own sync process (its `docs/upstream-maintenance.md` records an Angelica GLSM
sync on 2026-09-18 and Celeritas syncs up to the same `06999aab`).

## 3. Burden hotspots

### 3.1 The Celeritas seam

What exists: 21 mixins in `mixins.demonica.celeritas.json` on 16 Celeritas
classes, each with a `@Patch` declaration, a ledger row, an upstream-PR draft,
and a group that the guard turns off as a unit. `AnchorExtractor` (504 lines)
pulls 316 anchors out of the compiled mixins at build time; `QuarantineGuard`
(430) hashes the installed jar and audits the anchors at startup;
`InjectionAudit` and `InjectionAuditExtension` (293) report injectors that
missed after MixinExtras ran; `AnchorInventoryTest`, `GuardDrillTest`,
`QuarantineLedgerTest`, `QuarantinePriorityTest`, `InjectionAuditTest` and
`verifyProductionAnchors` check all of it. Moving the pin is a five-step manual
procedure with dev runs and a snapshot to regenerate (`docs/celeritas/PIN.md`).

What upstream did in five days (pin `06999aab` to head `7e5e9d7e`):

| Upstream change | Quarantine patches on that class |
|---|---|
| `ShaderChunkRenderer` constructor gains a `FogService`; the legacy-GLSL downgrade moves into `ShaderLoader`; `ChunkShaderEnvironment` is introduced | S2 (HEAD-cancel on `begin`/`end`) |
| `DefaultChunkRenderer` constructor gains a `FogService` | S16 |
| `RenderSectionManager` gains an abstract `getFogService()` and reads fog through it | S1 |
| `VintageRenderSectionManager` implements `getFogService()`, its inner renderer constructor call changes | S3, S4, I1 |
| `CeleritasWorldRenderer` reads fog cutoff from `GLStateManagerFogService.INSTANCE` | S5, S6m, S6s, S9 |
| `GLStateManagerFogService` gains `INSTANCE`; the `FogService` SPI file and two other service files are deleted | S15 (BASE) |

Whether each anchor still resolves can only be known by running
`AnchorInventoryTest` against a build of `7e5e9d7e`; S15's seven HEAD-cancels
are on methods that did not change, so BASE probably survives, and S2's target
methods look unchanged. But the point stands: the next pin move is already due,
and every pin move costs the full procedure.

What upstream offers: `common` already has `ShaderModBridge`, which finds Iris
by reflection for the modern loaders, and the new `ChunkShaderEnvironment`
record is a natural home for per-renderer provider state. The draft in
`docs/celeritas/patches/README.md` estimates that one provider interface
retires S1 through S5, S6m, S6s, S9, S14, S16 and most of S8, and that the
meshing patches (S10, S11, S13) need one context-aware vertex encoder hook.
Upstream's README invites downstream use, its CONTRIBUTING asks for a DCO
sign-off, and the modern loaders wire Iris in-tree, so there is precedent.
None of the 23 drafts has been sent.

Also in the seam: C1 (Component Model Hider and LittleTiles geometry in the
meshing task), the LittleTiles rebuild mixin, the Extra Utilities 2 lazy-quad
race, the iChunUtil viewport contract, and the fast-block-renderer routing for
Snow! Real Magic! and ArchitectureCraft are compatibility between those mods and
Celeritas's mesher. They would be needed by any Celeritas user with those mods,
shader pack or not. They belong upstream, and the ledger's C1 draft already says
so.

### 3.2 GLSM, the redirector and the core profile

GLSM exists in Angelica because 1.7.10 has no `GlStateManager` and mods call
`GL11` directly. Demonica inherits the full design: a bytecode redirector that
rewrites every GL call in every class to `GLStateManager` (including Celeritas's
own `LWJGL3Service`), a state model with stacks, a fixed-function shader
generator (`ffp/`, 3,314 lines) and a display-list recorder (`recording/`,
4,162, plus `DisplayListManager` and friends, 1,782) so that the game can run on
an OpenGL 3.3+ core-profile context, which `MixinMinecraftCoreProfileDisplay`
requires unconditionally (it throws if no core context can be created; there
is no option and no compatibility-profile fallback). The spike in
`docs/celeritas/SPIKE.md` ran GLSM on Cleanroom's default compatibility
context successfully, so the core profile is a choice, not a requirement of
GLSM.

Parts of GLSM that Demonica carries but does not use, or uses only for the
core profile:

| Part | Lines | Status |
|---|---|---|
| `shader/` (GLSL to SPIR-V to GLSL ES translation) and the `lwjgl-shaderc`/`lwjgl-spvc` compile dependencies | 946 | For Angelica's future SDL GPU backend; only `CompatShaderTransformer` references it |
| GLES caps and format remaps, plus GLES branches in eight files | ~130 plus branches | Angelica's Android/GL4ES path; no GLES target on Cleanroom |
| LWJGL service abstraction (`com.mitchej123.lwjgl`, `LWJGL3Service`, `RenderBackend`, `Lwjgl3GLRenderBackend`, `BackendManager`) | 3,877 | Abstracts LWJGL 2 versus 3; Cleanroom only has LWJGL 3. Eight files use it; 181 files across the three trees import `org.lwjgl.opengl` directly anyway |
| `com.mitchej123.glsm` service interfaces, providers and pass-through implementations, plus `AngelicaGLStateManagerService` | ~1,000 | Nothing calls `GLStateManagerServiceProvider`; upstream Celeritas does not reference `com.mitchej123`; `verifyDistributedJar` still requires the service file |
| GLU quadric ports (`AngelicaSphere` and siblings) | 703 | Only reached through the redirector's GLU mapping |
| `compat/mojang` Sodium-era shims | ~800 | Five of them unreferenced; the rest used by a handful of files |
| `FeedbackManager`, `QuadConverter`, `TransformOptimizer`, `dsa/` | ~1,400 | Used; part of the core-profile path or Iris's DSA abstraction |
| `GLStateManager.java` | 8,354 | One file, 5,112 lines different from Angelica's |

Compat code that exists because of GLSM's state cache or the redirector
(from the classes' own comments): CodeChickenLib's `GlStateTracker`, Extra
Utilities 2's `GLStateAttributes`, HBM's `RenderUtil` and its early lightmap
hook, Botania's two texture-state fixes, the CoFH tab blend fix, VoxelMap's
scissor and alpha-bit fixes, Lumenized's bloom state guard, the StellarCore and
Gnetum HUD-cache transformers. Compat that exists because of the core profile:
Old Research's client-array tessellator, VoxelMap's FBO path, the macOS
forward-compat transformer, and the 1,800-line end-portal replacement renderer
(vanilla's end portal uses eye-linear texgen, which core profile lacks).

MC coupling: Angelica builds its `glsm` module Minecraft-agnostically with three
stub classes; Demonica's GLSM imports Minecraft in 24 files. Angelica does not
publish GLSM as a standalone artifact (only the whole mod, `Angelica` 2.2.19 on
the GTNH Nexus), so consuming upstream GLSM the way Celeritas is consumed is
not available today without asking for it.

### 3.3 Actinium's performance features

All behind `DemonicaOptions.advanced` or `quality`, none read by a shader pack:

| Feature | Code | Compat that exists only for it |
|---|---|---|
| Deferred draw batching | `DeferredDrawBatcher` (491), `GuiGlStateBoundary` (206) | Obscure Tooltips (entity surface), Revo UI (compositor, gradient relocation) |
| Batching font renderer | `BatchingFontRenderer` (966), providers, `MixinFontRenderer` (200), `FontConfig` | NeoFontRender palette |
| Fast lit items and their display lists | `FastLitItemDisplayListCache` (357), `ItemVertexAlpha*`, `ItemRenderStateBoundary` | NeverEnoughAnimations alpha override |
| Model renderer batching and display lists | in `ModelRendererIrisMixin` (329, shared with Iris hooks) | Gibbed |
| Streaming draw paths | `VanillaVertexBufferRenderer` (269), `VanillaBufferBuilderRenderer` (120), `BufferBuilderStreamingDrawer` (265), `render/vertex` writers, `MixinTessellator`, both uploader mixins, `MixinVertexBuffer` | Old Research tessellator |
| Title-screen panorama | `PanoramaRenderer` (310), `MixinGuiMainMenu`, five shader files | |
| Window modes, loading-screen frame limit, dynamic FOV, biome colour noise settings | `DemonicaWindowModeController` (216), `FullscreenMode`, option pages | biome colour noise is stored but applies nothing on upstream (ledger) |

Iris has none of these. In the modern ecosystem, performance is Sodium's job
and here it is Celeritas's. Every one of them is a place where Demonica can
break a mod that a pure shader mod would not touch.

**Status (2026-09-25, `feat/drop-perf-features`).** Deleted: deferred particle
batching, the batching font renderer and the NeoFontRender palette bridge, the
panorama, the fast lit item path and the NeverEnoughAnimation override, model
renderer batching and the Gibbed compat, `BufferBuilderStreamingDrawer`, window
modes, the loading-screen frame limit, dynamic FOV and the biome colour noise
settings. The table above was wrong in four places, found while doing it:
- Not all of it was behind options: the font renderer and the panorama were
  always on, and so are the draw mixins.
- `GuiGlStateBoundary` is not part of deferred batching. It is the GUI and HUD
  GL-state baseline, called from always-active Iris mixins
  (`EntityRendererIrisMixin`, `LayerArmorBaseIrisMixin`) and CoFH's compat, so
  the Obscure Tooltips and Revo UI entries are GUI-state and Iris correctness
  fixes and stay (Revo UI's HUD-cache branch is dead, finding 6).
- The streaming row is mostly the core-profile draw path, not an option:
  `MixinTessellator`, both uploaders, `MixinVertexBuffer`,
  `VanillaBufferBuilderRenderer` and `VanillaVertexBufferRenderer` stay. So do
  `BufferBuilderMixin`'s writers (`render/vertex`), Old Research's compat (it
  routes through GLSM's own `TessellatorStreamingDrawer`), GLSM's upload
  strategy option and direct memory access.
- `ItemRenderStateBoundary` is the foreign-draw bracket `IrisGLSMBridge` relies
  on, not part of the fast lit item path; the tile-entity batch guards stay too.

### 3.4 Reese's Sodium Options

`me/flashyreese` (68 files, 9,360 lines) plus `gui/rso/compat` (24 files,
1,260 lines re-creating `Component`, `Style`, `KeyEvent`, `GuiEventListener`,
`NarrationElementOutput` and the rest of Mojang's 1.20 GUI API for a 1.12.2
screen). It needs the O1 accessors on Celeritas's slider and cycling controls,
the OPTIONS guard group, `OptionDefaults`, `OptionsScreens` (a `GuiOpenEvent`
listener that swaps Celeritas's screen for RSO's), five tests, three language
files, and two hard-coded entries in `verifyDistributedJar`. The ledger's
Checkpoint 7 records that with RSO off "Video Settings keeps Celeritas's
screen, which lists Demonica's settings and RSO's page". On modern versions RSO
is a separate optional mod; nothing about it is Iris.

### 3.5 Mod compatibility

15 conditional configs (32 mixins), 3 class transformers, 25 compat classes,
and 29 mods resolved at build time (34 CurseMaven and 5 Modrinth coordinates,
each pinned to one file id). Classified by what causes each entry, from the
classes' own comments:

| Cause | Entries |
|---|---|
| Shader integration (Iris's job), or platform co-existence | Distant Horizons (mixin plus 1,515-line DH compat; the mixin retires once DH accepts mod id `demonica`, draft in `docs/compat/distant-horizons.md`), Scannable (OptiFine probe, depth texture format), HBM weapon depth, Lumenized depth texture sharing, Kirino co-existence (Cleanroom's render delegate) |
| Celeritas mesher or viewport (belongs upstream) | C1 and `LittleTilesCompat`, Component Model Hider, LittleTiles rebuild signal, Extra Utilities 2 lazy quads, iChunUtil portals (3 mixins, 4 classes), Snow! Real Magic!, ArchitectureCraft, `FastBlockRendererCompat` |
| GLSM state cache or redirector | CodeChickenLib, Extra Utilities 2 GL state (2), HBM `RenderUtil` and early lightmap, Botania (2), CoFH Core, VoxelMap scissor and HUD cache (2), Lumenized state guard, StellarCore and Gnetum transformers |
| Core profile | Old Research, VoxelMap FBO path, macOS transformer |
| Performance features | NeverEnoughAnimations, NeoFontRender, Gibbed (all three removed on `feat/drop-perf-features`); Revo UI (3) and Obscure Tooltips were misfiled here: they are GUI GL-state fixes (3.3, Status) |
| Bugs in the other mod under any pipeline | Lumenized's uncleared bloom FBO and mismatched depth attachment (3 mixins) |
| Cleanroom Mixin bug workaround | `MixinReEntranceLockFix` (Techguns re-entrance) |

Four of the 27 entries in Appendix C are shader integration and one is
platform co-existence. Everything in the second row would be needed by a
Celeritas user without Demonica.

### 3.6 Diagnostics and dev tooling in the jar

| Component | Lines |
|---|---|
| `net/coderbot/iris/debug/IrisGlDebug` (66 public static methods, called from 18 files) | 2,789 |
| `net/coderbot/iris/debug/flight` (GL flight recorder, 20 files) | 1,667 |
| `PBRDebug`, `ShaderRegressionDebug`, `IrisDebugOptions` | 510 |
| GLSM `debug/` (`GLSMDebug`, `GLSMPerfDebug`, `GpuCheckpointTracker`) | 1,111 |
| Root `debug/` (`GlStateDiffProbe`, diagnostics, startup debug config) | 582 |
| Root `dev/` (`DevHarness`, `OptionsHarnessSteps`, installed by `Demonica.onInit`) | 518 |

About 7,200 lines, five percent of the jar. Several switches still carry
Actinium's names (`actinium.debug.textureUnitLogs`, `enableActiniumGlDebug`).

### 3.7 S8TNLib

The split gives S8TNLib clear provenance and a place for other Cleanroom mods
to consume GTNHLib. While Demonica is the only host, it costs:

- CI checks out S8TNLib and runs its Gradle build before every Demonica build.
- Contributors must clone the tag and `publishToMavenLocal` before Demonica
  compiles; `verifyS8tnlibPin` and `verifyRunClasspath` exist to police that.
- Two release trains for one consumer (S8TNLib 0.2.0 and Demonica 0.2.0
  shipped a day apart).

Inside the library: `bytebuf` (9 files, 5,699 lines, 55% of the library) is
GTNHLib's backport of LWJGL 3's `MemoryUtil`, `MemoryStack` and `PointerBuffer`
for LWJGL 2 on 1.7.10. Cleanroom ships LWJGL 3.3, so the real classes exist at
runtime; Demonica also carries a second wrapper,
`com.mitchej123.lwjgl.MemoryStack`, over the same thing. The `cel/` quad and
colour types (10 files, 1,351 lines) are copies of Sodium types that Celeritas
`common` ships under `org.embeddedt.embeddium` (`ColorABGR`, `NormI8`,
`ModelQuadView`, `ModelQuadFacing`); nothing in Demonica imports the S8TNLib
copies directly. `PostProcessingBridge` is set by Demonica and read only by
Demonica's Iris tree, so it is host code that happens to live in the library.

### 3.8 Build and toolchain

- **Unimined fork.** `1.4.36-kappa` from `maven.arcseekers.com`. Upstream
  Unimined 1.4.2 (2026-09-16) has `CleanroomPatcher`,
  `CleanroomMinecraftTransformer` and a `Cleanroom1_12_2Test`, and contains the
  DSL calls the build uses (`catchAWNamespaceAssertion`, `enableMixinExtra`,
  `defaultRemapJar`). Whether the fork carries fixes the build needs is not
  known from here; Actinium uses the same fork. Actinium's roadmap also notes
  Unimined's Gradle 10 compatibility as a risk.
- **Lombok** in 65 files (28 in GLSM, 36 in the Iris tree, 1 in the root) with a
  forked javac and `--sun-misc-unsafe-memory-access=allow`. Lombok must be
  updated for every JDK feature release.
- **Jabel** `Desugar` stub and eight `@Desugar` annotations on records; the
  build targets 21, where records need no desugaring.
- **Mixin configs** declare `compatibilityLevel: JAVA_8` while `MixinEarly`
  forces `JAVA_11` at runtime and the classes are Java 21.
- **Repositories** `maven.cleanroommc.com`, CurseMaven, Modrinth and the GTNH
  Nexus have no content filters, so Gradle probes Maven Central for their
  coordinates first (the 429 in this sandbox disabled Central and failed the
  S8TNLib build). The Celeritas and S8TNLib repositories are already filtered.
- **Formatting.** No formatter; the Iris tree mixes tabs and spaces
  (`IrisGlDebug` uses tabs). The `.git-blame-ignore-revs` entry is Angelica's.
- **Tests read the network**: `AnchorInventoryTest` needs the pinned Celeritas
  jar; the transformer tests need StellarCore, JourneyMap and Gnetum jars;
  `GlsmRedirectLinkageTest` runs the redirector over the whole Celeritas jar.

### 3.9 Leftovers and inconsistencies

- 35 classes that no main source, resource or build file references (Appendix
  B; two are reached only by tests), 1,626 lines, among them
  `AngelicaRenderQueue`, `DependencyVerifier`, `TransformerNarrower`,
  `EcosystemNarrowRules`, `LaunchWarn`, `JomlConversions`, the `fantastic/`
  batched-entity leftovers, `NoiseTexture`, `ColorTexture`,
  `SingleColorTexture`, `BlockStateConditionalIdMap`, `StringTransformations`.
- `GnetumHudCachingCompatTransformer` and `StellarCoreHudCachingCompatTransformer`
  are compiled, tested against downloaded jars, and shipped, but Demonica's
  `MixinEarly.getASMTransformerClass` registers only the macOS transformer and
  the early redirector; Actinium's registers all four. The ledger's Checkpoint 9
  did not run StellarCore or Gnetum.
- `META-INF/services/com.mitchej123.glsm.GLStateManagerService` is required by
  `verifyDistributedJar`, but nothing loads it (3.2).
- `docs/opengl_32_core.xml` (copied from Angelica's docs) is referenced by
  nothing.
- `scripts/provenance_audit.py`, `scripts/test_port_scan.py`,
  `docs/PROVENANCE.md` and `docs/provenance/` describe the sync era, which the
  fork ended; the tags they name exist on the remote.
- Actinium's names survive in `assets/actinium/**` (lang keys, the mod logo),
  `assets/angelica/**`, `IrisDebugOptions.Bridge.enableActinium*`,
  `actinium.debug.*` system properties, and DH's accessor label
  `ActiniumShaders`.
- `AngelicaMod`, `Tags`, `ClientProxy` and the `net.minecraftforge.eventbus`
  stubs (a 36-line stand-in for the EventBus 7 artifact Angelica depends on) are
  Angelica remnants in GLSM; the stubs are fine, the rest is noise.
- `third-party/actinium/THIRD_PARTY_NOTICES.md` inventories a renderer Demonica
  does not carry; `THIRD_PARTY_NOTICES.md` points at it, so it stays until that
  page is rewritten.

## 4. Recommendations

### 4.1 Narrow the scope

Ordered by leverage. Each item says what goes, why, and what it depends on.

**A. Send the Celeritas drafts, then shrink the quarantine to what upstream
will not take.** The provider interface (S1 to S5, S6m, S6s, S8, S9, S14, S16),
the meshing encoder hook (S10, S11, S13), the small getters (S7, S19, O1, I2,
S20), the fog-service selection (S15), and the two mesher hooks (C1). Start with
the provider interface: it is one file in `common`, inert without a shader mod,
and it carries half the quarantine. Send C1 and the other Celeritas-mesher
compat (3.5, second row) as separate upstream PRs; they are Celeritas
compatibility, not shader compatibility. While waiting, freeze the guard: no new
groups, no new levels. Expected effect once merged: 10 of the 23 ledger
entries gone with the provider interface alone (11 counting S8), all but S17
and I1 if the encoder hook and the small getters land too, most of
`celeritas/guard` and its tests gone, and pin moves become a version bump.
Dependency: upstream's willingness; the README invites it, and the DCO is
the only formality.

**B. Replace the anchor audit with an Iris-style version gate.** Iris refuses
to start with a Sodium it was not built for. Demonica can do the same: known
SHA-256 (or the auto-build's version string once upstream tags builds) means
everything applies; anything else means shaders off with the reason on the
shader pack screen. That keeps the one thing the guard is for (never crash on a
foreign Celeritas) and drops the anchor extraction, the 316-anchor audit, the
four levels, the drills, the inventory snapshot and the ledger-as-test-input
coupling. If the maintainer values the partial-degrade behaviour, keep it only
for BASE (S15) whose failure mode, solid-fog terrain, is the one that hurts
without a shader pack. Dependency: none; this is a subtraction. It also makes A
less urgent, since a moved anchor costs a bump instead of a debugging session.

**C. Drop Reese's Sodium Options.** 10,620 lines, O1, the OPTIONS group, five
tests, three lang files, two `verifyDistributedJar` entries, and the
`OptionsScreens` swap. Demonica's settings stay where they already are: in
Celeritas's pages, through the construction events `DemonicaOptionPages`
listens to. If the screen is wanted, it is a separate mod ("RSO for Celeritas")
that any Celeritas user can install; the port already depends only on
Celeritas's option API plus two accessors. Dependency: none.

**D. Move the performance features out.** *Done by deletion on
`feat/drop-perf-features`; what stayed and why is in 3.3's Status.*
Everything in 3.3 and the five compat entries that serve it. Two options: delete them (Celeritas is the
performance mod on this stack), or extract them into a sibling mod so users
who want the batching font renderer can keep it. The end-portal renderer is
the exception: it is required for as long as the core profile is (E).
Dependency: none for deletion; the extraction needs the same bridges the
Iris tree already uses for GLSM.

**E. Make the core profile optional, default to Cleanroom's compatibility
context, and measure.** `MixinMinecraftCoreProfileDisplay` currently throws if
no 3.3+ core context exists. Turn it into an option, off by default, with the
compatibility context as the normal path (the spike already ran GLSM on it).
Then measure what is still exercised: the FFP generator, the display-list
recorder, the streaming drawer, the end-portal renderer, the GLU ports and the
core-profile compat all become dead on the default path. macOS is the reason to
keep the option at all (its compatibility profile stops at OpenGL 2.1). This is
the prerequisite for the bigger decision in 4.4; it is also the single change
that removes the most compat entries at once.

**F. Delete the dead and the vestigial.** The 35 unreferenced classes, the
`com.mitchej123.glsm` service layer and its service file (adjust
`verifyDistributedJar`), the SPIR-V translator and its two compile
dependencies, the GLES paths, the five unreferenced `compat/mojang` shims, the
Gnetum and StellarCore transformers unless they are wired in (decide: they are
either a port omission or dead code; the tests and the two mod pins go with the
decision), `docs/opengl_32_core.xml`, and the Jabel stub. About 4,000 lines.
Dependency: none.

**G. Take the diagnostics out of the jar.** Move `dev/` to a `dev` source set
that `runClient` sees and `jar` does not. Put the flight recorder and
`IrisGlDebug`'s sampling and timing tables behind one compile-time module or a
`-Pdiagnostics` build flag; keep the one-line startup diagnostics and the
guard's reasons. Rename what is left away from Actinium's names. About 5,000
lines out of the release jar.

**H. Fold S8TNLib back, or make it a normal dependency.** With one host,
the cheapest honest arrangement is a git submodule plus `includeBuild`: the
submodule SHA is the pin, no publishing, no manifest check, CI needs only
`submodules: true`. If S8TNLib is meant to serve other Cleanroom mods, publish
its releases somewhere resolvable instead (a static Maven on a `gh-pages`
branch, Cloudsmith, or the Modrinth Maven if it gets a project page; GitHub
Packages needs a token even for public reads). Either way `verifyS8tnlibPin`
and the CI's double checkout go. Inside the library: replace `bytebuf` with
`org.lwjgl.system.MemoryUtil` and `MemoryStack` (and drop
`com.mitchej123.lwjgl.MemoryStack` on the Demonica side), use Celeritas's
`ColorABGR`/`NormI8`/`ModelQuadView` instead of the `cel/` copies, and move
`PostProcessingBridge` into Demonica as `Mods` and `IFontParameters` already
were. The library then keeps the tessellator capture, the VAO/VBO helpers and
the vertex formats, roughly 3,300 lines.

**I. Narrow the compatibility promise.** After A, D and E, what remains in-tree
is the first row of 3.5. State that in the README: Demonica carries compat
for mods that integrate with a shader mod (Distant Horizons, Scannable, HBM's
shader probe, Lumenized's depth), forwards renderer compat to Celeritas, and
reports other mods' GL-state bugs to those mods. Each retired entry also
retires a CurseMaven pin and its build-time download.

### 4.2 Modernise the stack

- **Try upstream Unimined 1.4.2.** It lists Cleanroom support and the DSL
  features the build uses. If it works, the arcseekers repository and the fork
  go, and Gradle 10 readiness follows upstream's schedule. If it does not,
  record why in `build.gradle` so the fork stays a known cost rather than an
  inherited one, and at least move to the current `1.4.43-kappa`.
- **Remove Lombok** with `delombok` and Java 21 records; delete the Jabel
  stub. This removes an annotation processor, the forked javac and a
  per-JDK-release upgrade.
- **Set the mixin configs' `compatibilityLevel`** to the highest level
  CleanMix's Mixin 0.8.7 accepts for the classes (they are Java 21) and drop
  the runtime override in `MixinEarly`.
- **Filter every repository** with `exclusiveContent` or `content {
  includeGroup }`: Cleanroom coordinates to `maven.cleanroommc.com`, `curse.maven`
  to CurseMaven, `maven.modrinth` to Modrinth, GTNH coordinates to the Nexus.
  This is what already protects Celeritas and S8TNLib, and it is what would
  have kept this sandbox's build alive through a Central outage.
- **Compile the Iris tree against `celeritas-common`** from
  `maven.taumc.org` (upstream publishes it for downstream use) in addition to
  the forge122 jar, once a published `common` version matches the pinned commit.
  Today `:shader` compiles against `common` as shaded and downgraded to Java 8
  inside the forge122 jar, where records are stubs. Low priority until upstream
  tags builds.
- **Adopt a formatter only for `com.demonica.*`.** Reformatting the synced
  trees would inflate every future diff against Actinium and Angelica.
- **CI**: cache Unimined's workspace with `gradle/actions/setup-gradle`'s
  cache paths, add a `concurrency` group, attach release jars from the tag
  build instead of by hand, and add Dependabot for Actions and Gradle plugins.
  Add a weekly job that resolves the newest Celeritas auto-build and runs
  `AnchorInventoryTest` in report mode (or, after B, the version gate's
  self-test), so upstream drift is a notification rather than a surprise.

The rest is already modern: Gradle 9.7, JDK 25 with `--release 21`, JUnit 6,
Actions v5 to v7, a Java 21 language level, and a clean multi-project layout
with `verifyModuleBoundaries`.

### 4.3 Reduce the maintainer's recurring work

- **Keep `shader/` and `glsm/` byte-close to Actinium's.** Today they are 403
  and 144 lines apart. Actinium syncs from Angelica and from Celeritas; as long
  as Demonica's own behaviour stays in the root project and behind the bridges
  the trees already have (`IrisDebugOptions.Bridge`, `WorldRendererCompatBridge`,
  `ShaderProviderHolder`, `RenderDebugHooksHolder`), taking Actinium's syncs is
  a three-way merge of a few hundred lines. The provenance tooling that was
  written for the sync era does exactly this audit; keep the script, retire the
  narrative docs to the fork-point tag.
- **Propose a shared engine to Actinium.** Actinium's
  `docs/project-structure-plan.md` plans compile-enforced module boundaries for
  a multi-maintainer future, and its `shader/` and `glsm/` would compile
  unchanged against a renderer-agnostic provider interface. The same interface
  that A proposes to Celeritas would let both mods share one Iris tree with only
  the seam differing. This is the largest possible burden reduction and the
  least under Demonica's control; it costs one conversation to find out.
- **Make docs describe, not enforce.** Move the ledger's machine-read columns
  (id, mixin, group) fully into `@Patch` and generate the markdown table from
  the compiled annotations in a Gradle task, so `QuarantineLedgerTest` checks
  code against code and the ledger can be edited freely. The same for
  `upstream-mixin-inventory.txt`: regenerate it as part of the pin bump rather
  than by hand.
- **Pin policy.** Decide a cadence (monthly, or when upstream lands something
  the pipeline wants) and make the bump a script: update three properties,
  regenerate the snapshot, run `check`, run the harness script, commit. After A
  and B the manual steps are the harness run only.
- **Report other mods' bugs to them.** Lumenized's uncleared FBO and mismatched
  depth attachment, Distant Horizons' hard-coded mod id, Scannable's `GL_R32F`
  pixel type. Each accepted fix retires a mixin.

### 4.4 The fork in the road: the GL layer

Everything above is trimming. The one structural decision is whether Demonica
keeps GLSM's design (state manager plus redirector plus core-profile
emulation) or adopts Iris's: hook the game's own state manager with mixins and
run on the profile the game already uses.

| | Keep GLSM, shrink it (4.1 E and F) | Iris-style: mixins on vanilla `GlStateManager`, compatibility profile |
|---|---|---|
| What goes | SPIR-V, GLES, the LWJGL abstraction, the service layer, dead shims: about 6,000 lines. With the core profile optional, another 10,000 become cold code | GLSM, the redirector, both transformers, FFP, display-list recording, streaming, the end-portal renderer, the core-profile display, the GLU ports, the state-cache compat (CCL, XU2, HBM, Botania, CoFH, VoxelMap, HUD caches): roughly 30,000 to 35,000 lines and 12 to 15 compat entries |
| What stays | The state model Iris's bridge relies on (blend, alpha, depth, fog, texture, program events), 1.12.2's raw-GL mods captured by the redirector | A new adapter from Iris's `StateUpdateNotifiers` and storage classes to mixins on `net.minecraft.client.renderer.GlStateManager` and `OpenGlHelper`; Angelica's `PassThroughGLStateManager` and the `GLStateManagerService` interface are a starting point for the shape |
| Risk | Continues to own 38,000 lines that Angelica changes by 16,800 lines per five months | Mods that call `GL11` directly bypass the tracker, as they do under Iris on modern versions and did under OptiFine on 1.12.2: shader passes can see wrong blend or texture state until the next vanilla call. macOS loses shaders unless Cleanroom itself grows a core-profile mode |
| Work | Weeks, incremental, no behaviour change on the default path | Months: a rewrite of the GL layer and a re-verification of every checkpoint in the ledger |
| Upstream alignment | Stays alignable with Angelica and Actinium | Diverges from both; aligns with Iris and Oculus |

Recommendation: do not start the rewrite now. Do E first, because it turns the
question into a measurement: with the compatibility context as the default,
the parts of GLSM that only the core profile needs stop running, and the
remaining state-tracking surface that Iris's bridge actually uses becomes
visible in the logs and in the guard's counters. If that surface is small, the
Iris-style adapter becomes a bounded project instead of a leap; if it is large,
the answer is to keep GLSM and ask Angelica to publish it as an artifact (its
`glsm` module is already built MC-agnostically with three stubs and has the
`maven-publish` plugin applied, so the request is small).

## 5. Suggested sequencing

| Phase | Items | Approximate lines removed from the jar | Notes |
|---|---|---|---|
| 1. Subtractions with no dependencies | F (dead code, service layer, SPIR-V, GLES), C (RSO), G (diagnostics out of the jar), repository filters, Jabel, mixin compat levels | 20,000 | One release; each item is its own commit and revertible |
| 2. Upstream conversations | A (provider interface to Celeritas), C1 and the mesher compat to Celeritas, the DH mod-id issue, the Lumenized and Scannable reports, the shared-engine question to Actinium, the GLSM artifact question to Angelica | 0 now; 3,000 to 6,000 when merged | Start these early; they run in parallel with everything else |
| 3. Guard to version gate | B | 3,000 plus 1,000 test lines and the ledger coupling | Do before the next pin move |
| 4. Performance features out | D and the three compat entries that were really its own | 4,700 in main sources (the draw path, `GuiGlStateBoundary` and two compat entries stay) | Done: deleted, no sibling mod |
| 5. Core profile optional | E, then measurement | 0 immediately; 10,000 to 15,000 cold | Feeds 4.4 |
| 6. S8TNLib | H | 7,000 in the library; the CI double build | Submodule plus `includeBuild` or a published artifact |
| 7. Toolchain | Unimined 1.4.2 trial, Lombok removal, formatter for `com.demonica`, CI jobs | 0 | Any time; Lombok removal touches 65 files, so after phase 1 to avoid conflicts |

After phases 1 through 6 the jar is roughly 85,000 to 90,000 lines: the Iris
pipeline and its hooks, a slimmer GLSM, a small seam, and shader-relevant
compat. That is the shape "Iris for 1.12.2" implies.

## 6. Open questions for the maintainer

1. Is macOS a target? It is the only reason the core profile is mandatory
   today, and the answer decides most of 4.4.
2. Should the StellarCore and Gnetum transformers be registered (as in Actinium)
   or deleted? They cannot stay as they are.
3. Is a sibling mod for the performance features wanted, or is deletion fine?
   *Answered: deletion.*
4. Will S8TNLib have a second host? If not, fold it back or submodule it.
5. Is Actinium open to sharing `shader/` and `glsm/` as modules? Their structure
   plan suggests the timing is right.
6. Should the Celeritas drafts go upstream under the maintainer's name, and in
   what order? A single provider-interface PR first is the suggestion here.

## Appendix A. How the numbers were produced

- Line counts: `find <tree> -name '*.java' | xargs wc -l`, per package with
  `awk`, in this tree at `11b6c61`.
- Tree diffs: a Python script hashing every `.java` by relative path across
  Demonica, Angelica `a23cefba`, Actinium `16fee8fc` (shallow clones of
  2026-09-25), then `diff -u --ignore-all-space` per differing file, counting
  added and removed lines.
- Celeritas drift: `git diff --stat 06999aab..7e5e9d7e -- forge122 common` on a
  clone of `stonecutter`, with the per-file diffs for the quarantine's targets.
- Dead classes: for each top-level class, a word-boundary grep of its simple
  name across the other main sources, resources, mixin configs, service files
  and `build.gradle`; zero hits means unreferenced. Test-only references were
  counted separately. This under-reports classes reached only by reflection or
  by string constants, and over-reports nothing.
- Usage of S8TNLib, the LWJGL abstraction, SPIR-V, GLES, DSA and the Mojang
  shims: grep of imports and simple names across the three main trees.
- Compat classification: the class-level comments of every file under
  `mixin/mod`, `mixin/early`, `compat` and `loading/fml`.
- Upstream facts: the clones above, `git ls-remote` for tags, Maven metadata
  from `maven.taumc.org`, `nexus.gtnewhorizons.com`, `maven.arcseekers.com`,
  `maven.wagyourtail.xyz`, and the raw READMEs of Unimined and Cleanroom's
  TemplateDevEnv.
- Build: `./gradlew publishToMavenLocal` in S8TNLib was attempted three times
  and failed each time on Maven Central rate limiting through the sandbox's
  proxy (429 on `com.cleanroommc:cleanroom`, then on `commons-parent`). No
  Demonica task ran. Nothing in this page depends on a build having run.

## Appendix B. Classes nothing references

Main sources, resources and the build script contain no reference to these
(test-only references in parentheses):

| Class | Lines |
|---|---|
| `com/demonica/gui/rso/compat/RenderPipelines` | 15 |
| `com/gtnewhorizons/angelica/rendering/AngelicaRenderQueue` | 69 |
| `com/gtnewhorizons/angelica/glsm/loading/DependencyVerifier` | 18 |
| `com/gtnewhorizons/angelica/glsm/loading/TransformerNarrower` | 36 |
| `com/gtnewhorizons/angelica/glsm/loading/EcosystemNarrowRules` | 10 |
| `com/gtnewhorizons/angelica/glsm/compat/FogHelper` | 21 |
| `com/gtnewhorizons/angelica/compat/mojang/ChunkOcclusionDataBuilder` | 155 |
| `com/gtnewhorizons/angelica/compat/mojang/ParentElement` | 97 |
| `com/gtnewhorizons/angelica/compat/mojang/ChunkSectionPos` | 89 |
| `com/gtnewhorizons/angelica/compat/mojang/CompatMathHelper` | 27 |
| `com/mitchej123/glsm/RenderSystemServiceProvider` | 28 |
| `com/mitchej123/glsm/GLStateManagerServiceProvider` | 32 |
| `net/coderbot/iris/JomlConversions` | 15 |
| `net/coderbot/iris/LaunchWarn` | 43 |
| `net/coderbot/iris/fantastic/IrisParticleRenderTypes` | 22 |
| `net/coderbot/iris/fantastic/WrappingMultiBufferSource` | 10 |
| `net/coderbot/iris/fantastic/PhasedParticleEngine` | 5 |
| `net/coderbot/iris/shaderpack/loading/SourceSet` | 13 |
| `net/coderbot/iris/shaderpack/transform/StringTransformations` | 127 |
| `net/coderbot/iris/shaderpack/materialmap/BlockMatch` | 17 |
| `net/coderbot/iris/shaderpack/option/OptionTests` | 68 |
| `net/coderbot/iris/vertices/ExtendingBufferBuilder` | 7 |
| `net/coderbot/iris/vertices/BufferBuilderPolygonView` | 42 |
| `net/coderbot/iris/vertices/BlockSensitiveBufferBuilder` | 7 |
| `net/coderbot/iris/gui/element/widget/OnPress` | 6 |
| `net/coderbot/iris/rendertarget/ColorTexture` | 37 |
| `net/coderbot/iris/rendertarget/SingleColorTexture` | 41 |
| `net/coderbot/iris/rendertarget/NoiseTexture` | 76 |
| `net/coderbot/iris/shadows/Matrix4fAccess` | 25 |
| `net/coderbot/iris/block_rendering/BlockStateConditionalIdMap` | 90 |
| `kroppeb/stareval/expression/BasicVariableExpression` | 31 |
| `kroppeb/stareval/function/BasicFunctionContext` | 36 |
| `kroppeb/stareval/function/I2FFunction` | 24 |
| `net/coderbot/iris/debug/flight/GlFlightRecordingDecoder` (1 test) | 137 |
| `com/demonica/loading/fml/transformers/GnetumHudCachingCompatTransformer` (4 tests) | 150 |

Classes referenced only by other classes on this list, or only by the
unregistered transformers, are not detected; a second pass after deleting these
will find more.

## Appendix C. Reference list of the compatibility entries

| Config or class | Mod | Mixins | Cause (3.5) |
|---|---|---|---|
| `mixins.demonica.distanthorizons.json`, `net/coderbot/iris/compat/dh` | Distant Horizons | 1 | Shader integration |
| `mixins.demonica.scannable.json`, `ScannableShaderCompat` | Scannable | 2 | Shader integration |
| `mixins.demonica.hbm.json` (`MixinItemRenderWeaponBase`), `HbmWeaponDepthCompat` | HBM | 1 | Shader integration |
| `mixins.demonica.lumenized.json` (`MixinDepthTextureUtil`) | GregTech CEu / Lumenized | 1 | Shader integration |
| `mixins.demonica.kirino.json`, `KirinoCompat` | Kirino Engine (Cleanroom) | 1 | Platform |
| `seam.ChunkBuilderMeshingTaskCompatMixin` (C1), `LittleTilesCompat`, `ComponentModelHiderCompat`/`Bridge` | LittleTiles, Component Model Hider | 1 | Celeritas mesher |
| `mixins.demonica.littletiles.json` | LittleTiles | 1 | Celeritas mesher |
| `mixins.demonica.extrautils2.json` (`MixinXUBlockStaticLazyQuads`) | Extra Utilities 2 | 1 | Celeritas mesher (threads) |
| `mixins.demonica.ichunutil.json`, `PortalRenderState`, `PortalViewportFactory`, `PortalViewportProvider`, `WorldBoxVisibility` | iChunUtil | 3 | Celeritas viewport contract |
| `SnowRealMagicCompat`, `ArchitectureCraftCompat`, `ArchitectureCraftRenderRouting`, `FastBlockRendererCompat` | Snow! Real Magic!, ArchitectureCraft | 0 | Celeritas fast block renderer routing (S13) |
| `mixins.demonica.ccl.json`, `GlStateTrackerSnapshot` | CodeChickenLib | 1 | GLSM redirector |
| `mixins.demonica.extrautils2.json` (`MixinGLStateAttributes`, `BooleanStateCapAccessor`), `ExtraUtils2GLStateCompat` | Extra Utilities 2 | 2 | GLSM redirector |
| `mixins.demonica.hbm.json` (`MixinRenderUtil`), `mixins.demonica.hbm.early.json`, `HbmRenderStateCompat` | HBM | 2 | GLSM state model |
| `mixins.demonica.botania.json`, `BotaniaGlStateCompat` | Botania | 2 | GLSM state cache |
| `mixins.demonica.cofhcore.json` | CoFH Core | 1 | GL state after GUI draw |
| `mixins.demonica.voxelmap.json`, `VoxelMapCompat` | VoxelMap | 3 | GLSM cache, core profile, HUD cache |
| `mixins.demonica.lumenized.json` (`MixinBloomEffectUtilStateGuard`), `BloomStateGuard` | GregTech CEu / Lumenized | 1 | GLSM state |
| `mixins.demonica.lumenized.json` (`MixinBloomEffectUtilClear`, `MixinRenderUtilDepth`, `MixinShadersDepthTest`) | GregTech CEu / Lumenized | 3 | Bugs in the other mod |
| `StellarCoreHudCachingCompatTransformer`, `GnetumHudCachingCompatTransformer` | StellarCore, Gnetum | 0 (transformers, not registered) | GLSM HUD cache |
| `MacDisplayForwardCompatTransformer`, `CoreProfileContextAttributes` | macOS | 0 | Core profile |
| `mixins.demonica.oldresearch.json`, `OldResearchTessellatorCompat` | Old Research | 1 | Core profile (client arrays) and streaming drawer |
| `NeverEnoughAnimationsAlphaOverride` | NeverEnoughAnimations | 0 | Fast lit item path (removed) |
| `NeoFontRenderCompat` | NeoFontRender | 0 | Batching font renderer (removed) |
| `mixins.demonica.gibbed.json`, `DemonicaModelRenderer` | Gibbed | 1 | Model renderer batching (removed) |
| `mixins.demonica.revoui.json`, `RevoScreenEffectsGradient` | Revo UI | 3 | GUI GL state boundary, HUD cache (dead branch) |
| `mixins.demonica.obscuretooltips.json` | Obscure Tooltips | 1 | GUI GL state boundary (Iris armor state in tooltips) |
| `MixinReEntranceLockFix` | Techguns and other legacy coremods | 0 | Cleanroom Mixin bug |
