# The Celeritas patch ledger

Demonica runs on the upstream Celeritas mod pinned in [`PIN.md`](PIN.md), and patches
Celeritas's own classes in one place only: the quarantine,
`mixins.demonica.celeritas.json` (package `com.demonica.mixin.celeritas`,
`seam` for forge122 classes and `internal` for the shaded `common`). Every
patch there has a row in this ledger and an upstream-PR draft in
[`patches/`](patches/). The drafts have **not been sent**. Whether and when to
propose them upstream is the maintainer's decision.

## Rules

- Only the quarantine touches Celeritas classes. Mixins elsewhere may target
  vanilla classes that upstream also patches, but not Celeritas's.
- Each quarantine mixin declares `priority = 1100`, above upstream's 1000 on
  `RenderGlobal` (instruction-level injections into a method another mixin
  merged need a strictly higher priority), and `remap = false` on Celeritas
  targets.
- No `@Redirect` and no `@Overwrite`: HEAD/RETURN injections, `@ModifyArg`,
  `@ModifyVariable`, `@ModifyExpressionValue`, `@WrapOperation`, `@Shadow`, and
  methods a mixin adds (S3's override, the ducks' accessors).
- The config is non-fatal (`"required": false`, `"defaultRequire": 0`), so a
  patch whose anchor moved does nothing instead of crashing the game.
  `QuarantinePlugin` logs one line per applied mixin and names every injector
  that found no target: `Applied <mixin> to <class>: n of n injectors found
  their targets`. A dev run that loads a world and opens Video Settings lists
  all 21 lines (a mixin is applied when its target class loads); anything short
  of `n of n` is a moved anchor. MixinExtras applies `@ModifyExpressionValue`,
  `@WrapOperation`, `@WrapWithCondition` and `@WrapMethod` in its own
  transformer extensions, after every config plugin's `postApply`, so the
  plugin holds its lines back until `InjectionAuditExtension`, inserted right
  after MixinExtras's extensions, has seen the finished class. If that
  extension cannot register, or a newer MixinExtras registers after it, the
  lines say how many injectors were "applied later by MixinExtras and not
  checked" instead.
- Each quarantine mixin names its ledger ids and its group with `@Patch`
  (`com.demonica.celeritas.guard`), and, where the patch relies on more than its
  injectors name, the calls it relies on (`context`) and the Demonica classes
  that carry its behaviour (`uses`). `QuarantineLedgerTest` checks the
  declarations against this ledger.
- The build extracts every patch's anchors from the compiled mixins
  (`AnchorExtractor`) into the mod jar. `AnchorInventoryTest` checks them
  against the pinned jar, along with upstream's mixin priorities and overwrites
  and a snapshot of upstream's whole mixin inventory; `verifyProductionAnchors`
  checks them in production names; `QuarantinePriorityTest` checks the rules
  above.

## Groups

What a patch's failure costs. When the installed Celeritas is not the pinned
build, the guard (below) turns off a whole group when any of its anchors moved,
except where the group says otherwise.

| Group | Patches | If the group fails |
|---|---|---|
| BASE | S15 | Terrain is drawn in solid fog colour, with or without a shader pack. |
| CORE_TERRAIN | S2, S5, S6m + S8, S9, S14 | Packs cannot draw terrain. Shaders are turned off with a named reason (L2). |
| SHADOW | S1, S3, S6s, S7, S16, I1, I2 | Shaders without terrain shadows (L1). |
| MESHING | S10, S11, S13 | Packs get no block IDs from terrain: plants do not wave, blocks fall back to the pack's defaults, and water is drawn in the translucent pass instead of the water pass. |
| OPTIONS | O1 | Reese's Sodium Options cannot draw sliders and cycling options, so Video Settings keeps Celeritas's own screen, which still lists Demonica's settings. |
| DEGRADE | S4, S17, S19, S20 | One feature degrades; see the row. Only the failing mixin is turned off. |
| COMPAT | C1 | The mods it serves lose that part of their rendering; see the row. Nothing else changes. Only the failing mixin is turned off. |

BASE is never turned off: each of S15's seven getters stands alone, Mixin skips
any that no longer match, and every one that still applies keeps fog right.

Two assignments differ from the plan:
- **I2 is in SHADOW, not CORE_TERRAIN.** The stamps that go backwards are the
  shadow search's: Iris's shadow counter restarts at 0 with every pipeline, and
  the lattice (with its separate shadow visit state) can outlive one. The
  terrain search is stamped by vanilla's frame counter, which only increases, so
  with terrain shadows off nothing goes backwards.
- **S17 is in DEGRADE.** A run without it (BSL, main pass over land, 2026-09-23)
  matched the frame with it to a mean difference of 0.2/255: Iris re-applies its
  pass after each terrain draw, and that is all. Translucent terrain and the
  shadow pass were not compared.

## The guard

Demonica is built for one Celeritas build ([`PIN.md`](PIN.md)), but a player can
install another. Before Mixin applies anything from the quarantine,
`QuarantineGuard` decides what may apply, and `QuarantinePlugin.shouldApplyMixin`
leaves out the rest. It reads class bytes only and never loads a Celeritas
class.

1. **Pin check.** It finds the jar Celeritas is loaded from (on the class path,
   or in the mods folder) and hashes it. A pinned SHA-256 applies every patch:
   the build has proven every anchor against that jar.
2. **Anchor audit.** Otherwise it checks every anchor against the installed
   jar's class bytes, read through the class loader. The anchors ship in the mod
   jar (`META-INF/demonica/celeritas-anchors`, with the pins), extracted at build
   time from the compiled quarantine mixins by `AnchorExtractor`:
   - each mixin's Celeritas targets, and what it shadows there;
   - the methods its injectors select, and the calls and field accesses their
     `@At`s name. For a vanilla target, only a method that upstream
     `@Overwrite`s belongs to Celeritas, and its body is checked in upstream's
     mixin (S8);
   - the methods it adds that override a supertype's (S3), which must stay
     inherited and not final;
   - every Celeritas class and member its own code uses, and the code of the
     helper classes its `@Patch` names;
   - its `@Patch` context: calls that make the patched code the code that runs
     (S1, S2, S13), and overrides that would bypass it (S2).

   Strings that a mixin annotation wrote go through the jar's refmap first, as
   Mixin resolves them, so vanilla members are checked by their production
   names; `verifyProductionAnchors` proves that against the SRG-named pin. A
   member a Celeritas class inherits from a vanilla or JDK type cannot be seen
   in Celeritas's bytes and passes.
3. **Levels.** What the failed groups leave of shaders:

| Level | When | Result |
|---|---|---|
| L0 | Nothing failed, or only MESHING, OPTIONS, DEGRADE, COMPAT or BASE | Shaders with terrain shadows; each failed group costs its own feature |
| L1 | SHADOW failed | Shaders without terrain shadows. The shader pack screen says so |
| L2 | CORE_TERRAIN failed | Shaders off: `IrisDebugOptions.enableIris()` and `enableCeleritas()` return false, the log names the anchor that moved, and "Shader Packs" opens a screen with the reason instead of the pack list |
| L3 | The anchor list cannot be read, or the guard itself fails | Shaders off, and only the patches outside CORE_TERRAIN, SHADOW and MESHING apply |

A MESHING failure is also named on the shader pack screen; the others are in the
log only. The log lists every anchor that did not hold and the mixins turned
off.

The audit does not check MixinExtras's `@Local` captures (S13, C1). A local that
changed type makes that injector miss, which the injection audit then reports
("n of m injectors found their targets"). Nor does it check what Demonica's
option pages rely on in Celeritas's own pages: a change there misplaces
Demonica's settings rather than breaking a patch, and `AnchorInventoryTest`
checks it at build time.

**Rehearsing it.** `-Ddemonica.guard.drill=<group|id|L3>[,...]` (in a dev run,
`-PdevProps=demonica.guard.drill=SHADOW`) treats groups or ledger ids as
failed even on the pin, and `L3` sets the anchor list aside.
`-Ddemonica.guard.audit=always` audits a pinned jar too. The development
workspace runs Unimined's remap of the pin, whose hash never matches, so every
dev run audits and logs "All N anchors ... hold".

## Patches

| Id | Mixin | Target | Change | Group | Upstream |
|---|---|---|---|---|---|
| [S1](patches/S1.md) | `internal.RenderSectionManagerMixin` | the deprecated `RenderSectionManager` constructor's `this(...)` call | `@ModifyArg` (index 8, static): `hasShadowPass` while a pack is active | SHADOW | not proposed |
| [S2](patches/S2.md) | `internal.ShaderChunkRendererMixin` | `ShaderChunkRenderer.begin`/`end` | HEAD-cancel: a pass of the shader configuration binds the pack's program and its state | CORE_TERRAIN | not proposed |
| [S3](patches/S3.md) | `seam.VintageRenderSectionManagerShadowMixin` | `VintageRenderSectionManager` (added method) | `isInShadowPass()`: the manager has a shadow pass and Iris is rendering shadows | SHADOW | not proposed |
| [S4](patches/S4.md) | `seam.VintageRenderSectionManagerMixin` | `VintageRenderSectionManager.useFogOcclusion` | No fog occlusion while a pack that turns vanilla fog off is active | DEGRADE: under such packs, terrain beyond vanilla's fog distance is culled. Inert while S15 reports planar fog, which upstream's fog occlusion never applies to | not proposed |
| [S5](patches/S5.md) | `seam.CeleritasWorldRendererMixin` | `CeleritasWorldRenderer.chooseVertexType` | HEAD-cancel: the pack's extended vertex format | CORE_TERRAIN | not proposed |
| [S6m](patches/S6m.md) | `seam.CeleritasWorldRendererMixin` | `CeleritasWorldRenderer.createChunkRenderMatrices` | HEAD-cancel: eye-anchored model-view for the draw S8 moved to the eye | CORE_TERRAIN, with S8 | not proposed |
| [S6s](patches/S6s.md) | `seam.CeleritasWorldRendererShadowMixin` | `CeleritasWorldRenderer.createChunkRenderMatrices` | HEAD-cancel in the shadow pass: Iris's shadow projection and model-view | SHADOW | not proposed |
| [S7](patches/S7.md) | `internal.SimpleWorldRendererAccessMixin` | `SimpleWorldRenderer.currentViewport`, `createChunkRenderMatrices` | Duck (`SimpleWorldRendererAccess`) for `CeleritasWorldRendererCompat`, the shadow pass's terrain adapter | SHADOW | not proposed |
| [S8](patches/S8.md) | `seam.RenderGlobalTerrainMixin` | upstream's `RenderGlobal.renderBlockLayer(BlockRenderLayer, double, int, Entity)` overwrite | HEAD/RETURN: Iris terrain phases and translucent prelude; vanilla's one-argument overload for the translucent layer (Distant Horizons' anchor); `@ModifyArg` of the camera Y to the eye; a debug-only `@WrapOperation` of the draw (`GlStateDiffProbe`, perf-debug timing) | CORE_TERRAIN, with S6m | not proposed |
| [S9](patches/S9.md) | `seam.CeleritasWorldRendererMixin` | `CeleritasWorldRenderer.renderBlockEntities(TileEntityRenderContext)` | HEAD/RETURN: Iris's block-entity phase (and perf-debug timing) | CORE_TERRAIN | not proposed |
| [S10](patches/S10.md) | `seam.ChunkBuilderMeshingTaskMixin` | `ChunkBuilderMeshingTask.execute` (full descriptor): its `canRenderInLayer` and vanilla `renderBlock` calls | In a build for the shader passes: `@WrapOperation`s that render a block the pack moves only in the pack's layer, and attach each vanilla-path block's context (`ShaderBlockContexts`) to the quads it draws | MESHING | not proposed |
| [S11](patches/S11.md) | `seam.VintageChunkBuildContextMixin` | `VintageChunkBuildContext.convertVanillaDataToCeleritasData`, `copyBlockData` | `@WrapOperation`s: the layer's quad contexts go to `copyBlockData`, which prepares the extended vertex encoder with each quad's context; translucent fluid quads go to the pack's water pass | MESHING | not proposed |
| [S13](patches/S13.md) | `seam.ChunkBuilderMeshingTaskMixin`, `seam.VintageBlockRendererMixin` | the `USE_NEW_BLOCK_RENDERER` read in `execute`; `VintageBlockRenderer.renderBlock`, `renderQuadList`, `getVertexLight`, `writeGeometry` | The fast block renderer is Demonica's option (off by default), per block: blocks that mods render or hide through vanilla's dispatcher stay on the vanilla path (`FastBlockRendererCompat`). Under a pack it writes each block's context, keeps fluids' material and the pack's layers, and applies the pack's directional-shading and separate-AO settings | MESHING | not proposed |
| [S14](patches/S14.md) | `seam.VintageRenderPassConfigurationBuilderMixin` | `VintageRenderPassConfigurationBuilder.build` | HEAD-cancel while a pack is active: `ShaderPassConfigurations` (separate water pass, semantic and depth-write defines) | CORE_TERRAIN | not proposed |
| [S15](patches/S15.md) | `seam.GLStateManagerFogServiceMixin` | `GLStateManagerFogService` (all 7 getters) | HEAD-cancel: GLSM's fog state, planar shape | BASE | not proposed |
| [S16](patches/S16.md) | `internal.DefaultChunkRendererMixin` | the `useBlockFaceCulling()` call in `DefaultChunkRenderer.render` | `@ModifyExpressionValue`: no face culling in the shadow pass | SHADOW | not proposed |
| [S17](patches/S17.md) | `internal.GlProgramMixin` | `GlProgram.<init>(int, Function)`, `destroyInternal` | Register every linked program with Iris's `DepthColorStorage` | DEGRADE: Iris re-applies its pass after each terrain draw | no upstream change needed; candidate for removal |
| [S19](patches/S19.md) | `internal.ChunkTrackerMixin` | `ChunkTracker.requiredNeighborRadius` | `@Shadow` read behind `ChunkTrackerAccess` | DEGRADE: Distant Horizons' neighbour-radius uniform falls back to its default | not proposed |
| [S20](patches/S20.md) | `seam.VintageBlockRendererQuadsMixin` | the `IBakedModel.getQuads` calls in `VintageBlockRenderer.renderBlock` | `@WrapOperation`: registered `BlockQuadTransformer`s see the quads of each face the fast renderer draws, and the unassigned quads | DEGRADE: addons' transformers do not run | not proposed |
| [C1](patches/C1.md) | `seam.ChunkBuilderMeshingTaskCompatMixin` | `ChunkBuilderMeshingTask.execute` (full descriptor): the whole method, its `canRenderInLayer` call, its `convertVanillaDataToCeleritasData` call | Only while the mod is installed: the Component Model Hider's build flag around the build (`@WrapMethod`) and no geometry for its hidden positions; LittleTiles' cached tile geometry appended to the vanilla-format buffers before they are converted | COMPAT: hidden multiblock parts are drawn, or LittleTiles blocks that are not full blocks are invisible | not proposed |
| [O1](patches/O1.md) | `internal.SliderControlMixin`, `internal.CyclingControlMixin` | `SliderControl.min`, `max`, `interval`, `mode`; `CyclingControl.allowedValues` | `@Shadow` reads behind `SliderControlAccess` and `CyclingControlAccess` (read through `OptionControls`), for Reese's Sodium Options' own slider and cycling rows | OPTIONS | not proposed |
| [I1](patches/I1.md) | `seam.VintageRenderSectionManagerShadowMixin` | `VintageRenderSectionManager.getAsyncOcclusionMode` | RETURN: "Everything" becomes "Only Shadows" when the manager gets a shadow pass | SHADOW | a question for upstream |
| [I2](patches/I2.md) | `internal.SimpleWorldRendererMixin` | `SimpleWorldRenderer.setupTerrain`, `setupShadowTerrain` | `@ModifyVariable(HEAD)` of `frame` to `DemonicaFrameClock.next()` | SHADOW | not proposed |

S18 is not a patch: the shadow pass's block entities come from the public
`SimpleWorldRenderer.forEachVisibleBlockEntity` (see S7). Neither is S12: each
vanilla-path quad's context is recorded in vanilla's `BufferBuilder`
(`BufferBuilderMixin` in `mixins.demonica.iris.json`, through
`QuadContextRecorder`), one entry per quad, so geometry that other code draws
between two blocks cannot shift the contexts of later quads.

Verified in the dev client on 2026-09-23: all 18 mixins apply, and all 42 of
their injectors find their targets, the `@WrapOperation`s and
`@ModifyExpressionValue`s among them (checked after MixinExtras applied them).
With S16's anchor pointed at a method that does not exist, a run warns that the
injector found its target in 0 of 1 methods, and the game runs on. The bytecode
exported with `-PmixinExport` shows S16's injection in
`DefaultChunkRenderer.render`, S8's `@ModifyArg` before `drawChunkLayer`, and
I2's `@ModifyVariable` at the head of both searches.

Checkpoint 10 (run/client/scripts/guard-*.txt, each run with its
`-PdevProps=demonica.guard.drill=...`), 2026-09-24: each drill loads a world, takes a
frame without a pack, opens Video Settings and the shader pack screen, and selects
BSL. Without a drill, all 316 anchors hold, and all 21 mixins apply with 46 of 46
injectors. SHADOW gives L1: 15 mixins apply, BSL draws terrain without shadows, and
the pack screen says why. CORE_TERRAIN gives L2: 17 mixins apply, and "Shader Packs"
opens the screen with the reason instead of the pack list. MESHING (18 mixins apply)
stays at L0, and the pack screen names what it costs. OPTIONS (19) stays at L0, and
Video Settings keeps Celeritas's screen, with Demonica's settings and RSO's page in
it. DEGRADE with COMPAT turns off its five mixins one by one (16 apply). L3 applies
only the 8 mixins outside CORE_TERRAIN, SHADOW and MESHING. In every drill, each
mixin that applied found all its injectors' targets, and the log shows no errors
beyond the development environment's usual ones. The build covers the rest (see
the rules above): among others, `GuardDrillTest` gives the guard class bytes with a
renamed method or a missing class (17 cases), and `verifyProductionAnchors` finds
all 316 anchors in the SRG-named pin.

The production-shaped smoke test (run/smoke-*.log, the same script as the drills),
2026-09-24: the mod jar from `build/libs` in a Prism instance on Forge
14.23.5.2864, which Cleanroom Relauncher restarts in Cleanroom 0.6.13-alpha on
Java 26, with Fugue and Scalar. With the Maven jar, the guard recognised the first
pin (`1579bd31`) and applied every patch without an audit. With the release asset,
renamed to sort after Demonica's jar, and `-Ddemonica.guard.audit=always`,
Demonica's coremod found Celeritas in the mods folder, the guard recognised the
second pin (`4dd4b35d`), and all 316 anchors held in production names. Both runs
applied all 21 mixins with 46 of 46 injectors, opened RSO and the shader pack
screen, and drew BSL with terrain shadows. Cleanroom examines coremod jars in
case-insensitive name order, so both pinned file names reach the class path
before Demonica's; the mods-folder search serves jars named to sort after it. The
one error in the logs is upstream's: `mixins.celeritas.json` names no refmap,
which CleanMix reports as "Invalid REFMAP JSON" with Celeritas alone too.

Checkpoint 9 (run/client/scripts/cp9.txt and cp9b.txt, with `-PwithCompatMods`),
2026-09-24: with Distant Horizons, iChunUtil, LittleTiles, NeverEnoughAnimation,
Snow! Real Magic!, ArchitectureCraft, Scannable, FluxLoading, Extra Utilities 2,
CodeChickenLib, CoFH Core, Botania and Obscure Tooltips loaded, every compat config
of a loaded mod registers, and the mixins whose targets loaded apply without
errors; all 21 quarantine mixins apply, with 46 of 46 injectors (C1 adds 3).
Distant Horizons binds its Iris accessor ("Registered mod compatibility accessor
for: [ActiniumShaders]"), and FluxLoading applies its own Celeritas mixin. With
the fast block renderer on, Snow! Real Magic!'s snow layers take the vanilla path
and show the plants it keeps inside them. Terrain, shadows and Video Settings work
with all of them loaded, without a pack and under BSL. RLFoliage (Better Foliage)
was not run: it needs FermiumBooter.

Checkpoint 8 (run/client/scripts/cp8.txt, cp8b.txt, cp8c.txt), 2026-09-24: all 20
mixins apply, and all 43 of their injectors find their targets (S8's debug
`@WrapOperation` is the 43rd). The title screen draws Demonica's blurred panorama,
which turns between two frames; text is drawn by the batching font renderer on the
title screen, in chat, on the F3 screen (with Demonica's and Kirino's lines), in
item tooltips and on a sign in the world, with and without BSL. A 3x3 end portal
shows its starfield without a pack and under BSL; the chest, enchanting table,
banner and sign next to it render through the tile-entity batch guards with no
attrib-stack imbalance. The creative inventory's items (the fast lit item path)
render in both. A half-broken block shows its crack overlay, nearest-filtered.
Dynamic FOV sits after Vignette in the Quality page.

Checkpoint 7 (run/client/scripts/cp7a.txt, then cp7b.txt), 2026-09-24: all 20
mixins apply (O1's two when Video Settings first opens). Options, then the Video
Settings button, opens Reese's Sodium Options: Celeritas's four pages, with
Demonica's settings where Actinium had them (Fullscreen Mode and the loading-screen
frame limit in Window, the fast block renderer in Sorting, GLSM's upload strategy
and the draw fast paths in CPU Saving, two groups at the end of Advanced), Iris's
pages and RSO's own. One change in each storage (Demonica's, Celeritas's,
vanilla's `options.txt`, RSO's) is applied, written to its file, and back after a
restart. The mod list's Config button opens RSO too; with RSO's "enabled" off,
Video Settings keeps Celeritas's screen, which lists Demonica's settings and RSO's
page.

Checkpoint 6 (run/client/scripts/cp6.txt; a water pool and tall grass at spawn),
with the fast block renderer off and on: the extended vertex encoder receives
the packs' IDs (BSL: tall grass 10000, leaves 10500, flowers 10100, water 20000
as a fluid; Complementary: tall grass 10005, water 32000 as a fluid; blocks the
pack does not name, -1). Tall grass is lit and waves (two frames half a second
apart differ on every plant and its shadow, and nowhere on static terrain), and
water is drawn by each pack's water program. The two paths give the same IDs and
the same motion; they shade blocks differently where the pack turns off
directional shading or wants separate ambient occlusion, which only the fast
renderer applies (see S13).

## Port decisions

Actinium classes that changed shape on the way to upstream Celeritas.

| Actinium | Demonica | Why |
|---|---|---|
| `GlProgramIrisMixin` (on the fork's `GlProgram`) | S17 | Upstream's `GlProgram` is a Celeritas class, so the mixin moved into the quarantine. |
| `ParticleManagerCullingMixin` | ported to `mixins.demonica.iris.json` | It targets vanilla's `ParticleManager`, which upstream does not patch, and culls against upstream's public `SimpleWorldRenderer.getLastViewport()`. `IrisMixinConfigPlugin` still skips it when ParticleCulling is installed (both `@Redirect` the same calls). |
| The fork's `ChunkBuilderMeshingTask`, `VintageChunkBuildContext`, `VintageBlockRenderer` (their shader parts) | S10, S11, S13 | Re-expressed as patches on upstream's classes. The block context is resolved once, in `ShaderBlockContexts`, for both paths; flowing water and lava fall back to the still block's ID when a pack names only `water` or `lava`. |
| The fork's quad contexts (appended only while a block was being drawn) | `QuadContextRecorder` (S12) | One entry per quad, null when drawn without a context, so other code drawing between blocks cannot misalign them. |
| `MixinChunkBuilderMeshingTaskBetterFoliage` | not ported | RLFoliage 2.5.3 ships its own mixin on upstream's `org.taumc` meshing task (a `@Redirect` of `canRenderInLayer`, `@WrapOperation`s on both `renderBlock` calls; checked in its jar). Actinium needed the adapter only because its fork renamed the task. A second adapter would run Better Foliage twice. S10's and C1's `@WrapOperation`s compose with it: MixinExtras wraps a redirected call. Not run yet: RLFoliage needs FermiumBooter, which the dev environment does not have. |
| LittleTiles (`MixinChunkBuilderMeshingTaskLittleTiles`, `MixinTileEntityRenderManager`, `LittleTilesCompat`), `ComponentModelHiderCompat` | C1 in the quarantine; `MixinTileEntityRenderManager` in `mixins.demonica.littletiles.json` | The hooks on upstream's meshing task are C1, each behind its mod's `Mods` flag. LittleTiles' "cache built" signal now schedules the section rebuild on the client thread (`scheduleRebuildForChunk`) instead of touching the section graph from LittleTiles' rendering thread. The hider's neighbour rule needs no hook in the fast renderer: its hidden blocks' neighbours take the vanilla path (S13). |
| The fast renderer's gates (Snow! Real Magic!, ArchitectureCraft, the Component Model Hider, `MissingModelCompat`) | `FastBlockRendererCompat`, read by S13's per-block switch; `MissingModelCompat` not ported | Checked once per block and layer from the loop's `block` and `pos` locals. `MissingModelCompat` kept missing models off Actinium's fast path because Forge's `FancyMissingModel` draws its label with a `FontRenderer`, which the batching font renderer took over and which then crashed on mesh threads; the font renderer now leaves Forge's `SimpleModelFontRenderer` alone (a4dc4263), so missing models render as in vanilla on both paths. `performance.use_fast_block_renderer` stays off by default, like upstream's production setting. |
| `VintageChunkBuildContext.beginVanillaFluidRender` (Fluidlogged API fluids) | not ported | Upstream's own `FluidloggedCompat` draws those fluids, without a context, so they mesh with no block ID. Tagging them needs a hook in upstream's `FluidloggedCompat` (a Phase 9 candidate). |
| The fork renderer's lighting (no quad-normal shading, AO depth blending, no brightness-based quad orientation) | not ported | Renderer choices, not shader glue: upstream's lighting stands. |
| `MixinGuiOptions` (opened RSO from the Video Settings button) | `OptionsScreens`, a `GuiOpenEvent` listener | Upstream's own `MixinGuiOptions` already cancels that button to open its screen; a second cancelling HEAD injection on the same method would race it. Demonica swaps upstream's screen for RSO when it is displayed, with the screen being left as the parent. The mod list's Config button (`@Mod(guiFactory)`, `DemonicaGuiFactory`) goes the same way. |
| The fork's option-API extensions (`Option.getAppliedValue`/`getDefaultValue`/`resetToDefault`/`shouldHideControl`, `OptionImpl.setDefaultValue`, `OptionGroup` names, slider getters, `CyclingControl.getAllowedValues`, `ExternalPage`, `ExternalButtonControl`, `ControlValueFormatter.translateDisabledOrVariable`) | O1 for the slider and cycling getters; the rest without a patch | Undo is `Option.reset()`, and "changed" is `Option.hasChanged()`, which upstream already defines against the applied value. Declared defaults are registered with RSO (`OptionDefaults`, held weakly). No page ever set a group name, so group headers stay empty as before. `ExternalPage` and `ExternalButtonControl` moved into RSO's package; `shouldHideControl` was never read. |
| `ActiniumGameOptionPages` (the fork's copies of the General, Quality and Advanced pages) | `DemonicaOptionPages`, listeners on Celeritas's construction events | Upstream's pages are shown as upstream builds them; Demonica's settings are added into their groups (`OptionGroupConstructionEvent`), at the end of Advanced (`OptionPageConstructionEvent`), and as pages (`OptionGUIConstructionEvent`: Iris's, the Debug page, RSO's own). Both screens get them. Upstream's fast-renderer toggle, which only sets the static flag S13 overrides, is replaced by Demonica's. The fork-only multidraw mode is gone; dynamic FOV waits for its mixin (Phase 8); the biome colour noise settings are not shown, since nothing applies them on upstream (Actinium did in its forked biome colour cache). |
| `ActiniumOptionHost` (pages collected once per game) | `DemonicaOptionHost`, collected for each screen | Celeritas builds its pages for every screen: slider ranges (GUI scale, chunk threads) and tooltips that depend on the window or the shader pack are current. |
| `OptionGUIConstructionBridge` (each listener isolated and rolled back) | one `post`, then drop what RSO cannot show | Upstream's `EventHandlerRegistrar` has no per-listener dispatch. A throwing listener ends the loop; the pages added before it are kept (see O1's draft). |
| RSO's text conversion (the first translation key) | the first key that has a translation | Upstream lists fallback keys for keys 1.12.2 lacks (the default graphics quality's `options.gamma.default`), as its own screen resolves them. |
| The option labels in the fork's `assets/celeritas/lang` | `assets/actinium/lang` | The jar ships no `assets/celeritas`. The keys keep their names; `DemonicaOptionPagesTest` checks every key of Demonica's settings in all three languages. |
| Actinium's vintage mixins that duplicate upstream's (`MixinDirection`, `MixinClassInheritanceMultiMap`, `MixinSplashProgressCallable`, the frustum pair, the `ActiveRenderInfo`, `BlockColors`, `Chunk` and `ChunkProviderClient` accessors, `MixinBakedQuad`, `MixinBakedQuadFactory`, `MixinBiomeColorHelper`, `MixinClientChunkManager`, `MixinSimpleBakedModelBuilder`, `MixinWorldClient`, the mipmap pair, `MixinEntityRenderer` and `MixinGuiIngameForge` (options), the piston renderer, the texture trio; `MixinGuiOptions` and `MixinRenderGlobal` are rows of their own) and its three unused `GlStateManager` accessors | dropped | Upstream applies the same mixins; a second copy would double every injection and fail on every `@Overwrite`. Compared one by one with upstream's at the pin: most are identical apart from their names. Where Actinium's copy carried more, the extra was fork behaviour: the frustum pair's raster-culling matrix (raster culling is hard-off upstream), the atlas-size getters of `MixinTextureAtlas` (nothing read them), the ActiveRenderInfo accessors that iChunUtil's compat needs (Phase 9 declares its own), and the three fixes below. `MixinRenderGlobal` is re-expressed as S8 and S9; nothing read the `GlStateManager` accessors. |
| The fork fixes inside those duplicates: biome colour noise in `MixinBiomeColorHelper` (and the fork's biome colour cache), the chunk tracker's per-tick reconcile in `MixinClientChunkManager`, the sprite transparency loop kept to int arithmetic in `features.mipmaps.MixinTextureAtlasSprite` (Actinium #171) | lost; upstream candidates ([Lost fork fixes](#lost-fork-fixes)) | The noise and the sprite loop sit in methods that upstream's mixins own (an `@Overwrite` at priority 1200, a private method of a mixin), so Demonica cannot take them over without patching upstream's mixin classes. The reconcile could come back: see its entry. |
| The vanilla draw fast paths (`MixinTessellator`, the two uploaders, `MixinVertexBuffer`, `BufferBuilderMixin`'s overwrites, `ModelRendererIrisMixin`'s batching and display lists, the fast lit item path) | ported in Phases 3 and 4 | Upstream patches none of `Tessellator`, `BufferBuilder`, `VertexBuffer`, the uploaders or `ModelRenderer`, so none of the conflict rules applies. |
| `MixinFontRenderer` and Angelica's batching font renderer (`com.gtnewhorizons.angelica.client.font`, `FontConfig`, `FontRendererAccessor`) | ported (core config) | Upstream does not patch `FontRenderer`. The debug switches are `-Ddemonica.fontDebug` and `-Ddemonica.disableFontBatcher` (Actinium's `actinium.*`). `NeoFontRenderCompat` (NeoFontRender's colour palette) is mod compat, Phase 9. |
| `MixinGuiMainMenu` and `PanoramaRenderer` | ported (core config) | Upstream does not patch `GuiMainMenu`. The panorama draws through Celeritas's `GlProgram`, but its matrix uniform is Demonica's `GlUniformJomlMatrix4f`: Celeritas's `GlUniformMatrix4f` takes the relocated JOML (see `CeleritasJoml`). `JomlBoundaryTest` now scans the mod's own classes as well as the shader tree. |
| `MixinRenderGlobalDestroyBlock`, `MixinTextureManagerDebugLabels`, `MixinEntityRendererDynamicFov` | ported (core config) | Their methods (`drawBlockDamageTexture`, `TextureManager.loadTexture`, `updateFovModifierHand`) are not touched by upstream's `RenderGlobalMixin` or options mixins. Dynamic FOV is a tick box after Vignette in the Details group; its Russian text is new (Actinium had none). |
| `MixinTileEntityRendererDispatcherBatch`, `TileEntityBatchDrawGuard`, `TileEntityGlStateGuard` | ported (core config); the guard moved to Forge's batch | Upstream's `TileEntityRenderDispatcherMixin` wraps only `getRenderer`. The batch-draw guard's `@Redirect` became a `@WrapOperation`. Actinium pushed and popped the GL state in its own renderer's two tile-entity loops; upstream's two loops (`CeleritasWorldRenderer.renderBlockEntities` and the `setTileEntities` loop inside its `RenderGlobal` handler) both sit between `TileEntityRendererDispatcher.preDrawBatch` and `drawBatch`, so the guard hooks those: saved when a batch opens, restored before the batch draws and after it. No quarantine patch is needed. |
| `GlStateDiffProbe` and the render-global stage timings | the probe and the `terrain-<layer>` and `block-entities-main` timings in S8 and S9 | They were hooks inside the fork's `renderBlockLayer` and tile-entity loop. The `block-entities-set` and `entities` timings, and the fork's per-draw terrain renderer timings, sat inside code upstream owns (its `RenderGlobal` handlers, `DefaultChunkRenderer`) and are gone. |
| Kirino (`KirinoCompat`, `MixinKirinoConfigHub`, `KirinoMixinConfigPlugin`) | ported; `mixins.demonica.kirino.json` registered by `MixinEarly` | Kirino ships with Cleanroom. Cleanroom 0.5.17 to 0.6.12 already turns Kirino's render delegate off in its own one-time config listener; the pin keeps it off if another listener turns it back on. |
| `NeverEnoughAnimationsAlphaOverride` | ported | NeverEnoughAnimation is a compile-only dependency; the override registers only when the mod is present. |
| Angelica's unused mixin interfaces (`ChunkTrackerAccessor`, `ClippingHelperExt`, `EntityRendererAccessor`, `GuiIngameAccessor`, `GuiIngameForgeAccessor`, `IGameSettingsExt`, `IPatchedTextureAtlasSprite`, `IRenderGlobalExt`, `ISpriteExt`, `PrimedEntityAccessor`, `RenderGameOverlayEventAccessor`, `RenderSectionManagerAccessor`, `ResourceAccessor`) | not ported | Nothing in Actinium used them. |
| `MinecraftOptionsStorage`, `VanillaBooleanOptionBinding` (the fork's copies of upstream's) | not ported | Only `ActiniumGameOptionPages` used them; upstream's pages bring their own. |
| Mod compat configs (`gibbed`, `ichunutil`, `lumenized`, `revoui`, `ccl`, `voxelmap`, `extrautils2`, `cofhcore`, `oldresearch`, `botania`, `hbm`, `scannable`, `littletiles`, `obscuretooltips`), `MixinLate`, the conditions file | ported: `mixins.demonica.<mod>.json`, `mixins.demonica.conditions.properties` | They target the mods' classes or vanilla's, which upstream does not patch, so they stay outside the quarantine. `MixinLate` registers nothing while Demonica is inactive (no Celeritas, or Actinium present). `mixins.demonica.hbm.early.json` joins the early configs, as in Actinium. VoxelMap is compiled against without the old Mixin its jar bundles. |
| Distant Horizons (`CleanroomMain.initializeModCompat` binds its Iris accessor only for mod id `actinium`) | `mixins.demonica.distanthorizons.json` (`MixinCleanroomMain`, optional) and an issue draft (docs/compat/distant-horizons.md) | A `@ModifyConstant` asks for `demonica` instead; `require = 0`, so a DH release without that constant still loads. |
| iChunUtil's world portals (`MixinPortalFrustum`, `MixinWorldPortalRenderer`, `MixinRenderGlobalProxy`, `PortalRenderState`, `PortalViewportFactory`) | ported (`mixins.demonica.ichunutil.json`), plus `AccessorActiveRenderInfo` in the core config | iChunUtil's portal frustum implements `ICamera` directly, so upstream's `setupTerrain` overwrite would fail to cast it to a `ViewportProvider`; the frustum now is one (its viewport through `CeleritasJoml`). The camera state is put back after each portal render, and the portal's own `RenderGlobal` reloads its Celeritas renderer. |
| Actinium's portal-camera `@WrapMethod` on `setupTerrain` and `PortalChunkRenderMatrices` | not ported | The wrapper ran vanilla's `setupTerrain` for portal cameras, and that body no longer exists under upstream's overwrite: vanilla's `renderInfos` stays empty for iChunUtil's portal `RenderGlobal`, whose entity loop reads it, so portal views draw terrain but not the entities and tile entities of that loop. The portal matrices are not needed: upstream takes terrain matrices from `ActiveRenderInfo`'s buffers, which the portal render sets and `MixinWorldPortalRenderer` restores. |
| `ChunkAnimatorCompat` | not ported (Chunk Animator has no effect) | It fed the fork's chunk animation API, which upstream does not have. Chunk Animator's own hooks are on vanilla's `RenderChunk`, which Celeritas does not use, so its animations do not run; nothing breaks. Upstream's chunk fade-in is the alternative. |
| `DepthsUpdateCompat` | not ported | Depths Update ships its own mixins on upstream Celeritas's classes. |
| `FluxLoadingCompat`, `FluxLoadingNotifyForwarder` | not ported | FluxLoading applies its own mixin on upstream's `RenderSectionManager.updateChunks` whenever the `celeritas` mod is loaded (seen in the Checkpoint 9 run). Actinium had to forward the signal only because its fork had another mod id. |
| The fork's Fluidlogged API classes (`FluidloggedCompat`, `FluidloggedBlockAccess`, `FluidStateStorage`) | not ported | Upstream has its own. Fluids it draws still mesh without a block context (see `VintageChunkBuildContext.beginVanillaFluidRender` above). |
| `TogglePassCommand` | not ported | Upstream Celeritas has one. |
| `MuiGuiScaleHook` (Modern UI's GUI scale range) | not ported | A copy of upstream's, used only by the fork's option pages. Actinium's minimum manual GUI scale of 5 on small displays is lost with it; upstream's pages use upstream's hook. |
| `NeoFontRenderCompat` | ported | Registered at init when NeoFontRender is installed: its colour palette follows the vanilla font renderer's colour codes. |
| The Extra Utilities 2 compat's use of `GlStateManager$BooleanState` | `demonica_at.cfg` opens it again | Upstream's AT opens it at runtime, but not on Demonica's compile classpath. |
| The remaining root tests | ported, apart from those that pin the fork's internals | `MixinConfigurationTest` checks Demonica's 20 configs (the quarantine is the one non-fatal config); `DependencyDirectionTest` allows the `com.demonica` classes the shader tree itself compiles. Skipped: the 24 under `dhj/embeddedt/**`, `VintageBlockRendererBindingContractTest`, the world and biome colour tests, `LightDataCacheTest`, `MuiGuiScaleHookTest`, `EmbeddiumStandardOptionsPagesTest`. |
| The fork's pass configuration (`VintageRenderPassConfigurationBuilder`) with pass consolidation on, Celeritas's default: one cutout pass staged under both `CUTOUT` and `CUTOUT_MIPPED` | S14's `ShaderPassConfigurations` stages it under `CUTOUT_MIPPED` only (`PassConfigurationTest`) | Celeritas draws every pass in a layer's stage each time vanilla draws that layer, and only the shadow pass skips a pass it has already drawn. So Actinium drew cutout terrain twice a frame, and so did Demonica under a pack until Phase 10. Upstream stages its consolidated pass under `SOLID`; S14 keeps it under `CUTOUT_MIPPED`, so that it is drawn in Iris's cutout-mipped phase, which packs read as `renderStage`. |

## Lost fork fixes

Behaviour of Actinium's fork (at `4a19c959`) that lived in code upstream owns,
and was lost when Demonica moved to upstream Celeritas. Upstream lacks all six
at the pin (checked against the pinned jar's bytecode). They are candidates to
propose upstream; none is drafted yet. Two are features rather than fixes.

### The sprite transparency loop under ZGC (Actinium #171)

- **What went wrong:** the per-pixel loop that decides a sprite's transparency
  calls `chooseNextLevel` for every pixel. Under ZGC, one C2 compile of it never
  finished and grew by about 200 MB/s until the JVM aborted; G1 was not
  affected (`36b69657`, "fix(mipmaps): avoid a C2 runaway compile under ZGC").
- **Actinium's fix:** `features/mipmaps/MixinTextureAtlasSprite` keeps an int
  ordinal maximum inside the loop and maps it back to the enum once
  afterwards; the result is the same.
- **Upstream:** forge122's `TextureAtlasSpriteMixin` (lines 56-82) still calls
  `chooseNextLevel` per pixel, and forge1710 and modern have the same loop.
  Whether upstream alone reproduces the crash is unverified: every run in #171
  had Actinium installed.
- **Why Demonica cannot carry it:** the loop is a private method of upstream's
  mixin (`embeddium$processTransparentImages`), called from its HEAD injection
  on `generateMipmaps`.
- **Proposal:** the same int-ordinal rewrite in all three loaders.

### Stale build results that keep their cancellation token (Actinium #113)

- **What went wrong:** a build result dropped as stale before it reached the
  token release left the section's build-in-flight bit set, and the graph
  search skipped the section for good: terrain missing at high altitude, and
  holes that approaching did not heal (`744df02c`). Actinium's trigger was the
  terrain and shadow frame counters being mixed; I2 removes that trigger here.
- **Actinium's fix:** `RenderSectionManager.releaseBuildCancellationToken`,
  called from both discard branches of `filterChunkBuildResults` for the
  latest submission, with a warning for the stale case.
- **Upstream:** common's `RenderSectionManager` drops such results without
  releasing the token (lines 749-750; the release is at 653-657), and
  `VisibleChunkCollector` (line 88) reads the stuck bit. Whether another path
  can still drop a latest result is unverified.
- **Why Demonica cannot carry it:** the branch is inside the private static
  `filterChunkBuildResults`.
- **Proposal:** release the latest submission's token on the discard path, and
  log it.

### In-flight builds that block updates (Actinium `744d6128`)

- **What went wrong:** a block or light update waits for the section's older
  build, which may already hold stale data, or starts a second build without
  cancelling the first, which can then upload its older mesh last ("Cancel
  stale chunk builds on updates", no issue).
- **Actinium's fix:** a rebuild request cancels and clears the in-flight token
  and re-queues the section (also in `scheduleRebuildAll`); results older than
  the section's latest submission (`RenderSection.lastSubmittedBuildFrame`) are
  dropped.
- **Upstream:** `RenderSectionManager` (lines 956-993) never touches the token;
  the interim list's submission (line 828) overwrites it without cancelling the
  old job.
- **Why Demonica cannot carry it:** it spans four section-manager methods and a
  new `RenderSection` field.
- **Proposal:** as Actinium did.

### Raster occlusion culling on 1.12.2 (a feature: Actinium #131, #172)

- **What it did:** Actinium wired upstream's bitraster culler for 1.12.2 (the
  option, a rotation-only view-projection matrix from the frustum mixins, and
  occluder boxes from the meshing task), turned it on by default ("about 100
  FPS"), and later budgeted it (`RasterBudget`).
- **Upstream:** forge122's `VintageRenderSectionManager` turns it off (lines
  61-63), its `FrustumMixin` supplies no matrix, and its meshing task builds no
  occluder boxes. The option still appears in `CommonOptionPages` (lines
  160-168) and does nothing on 1.12.2. Only the modern loaders run it.
- **Why Demonica cannot carry it:** three upstream sites have to change
  together: the switch, the viewport from upstream's frustum mixins, and the
  meshing loop.
- **Proposal:** wire it on 1.12.2 as the modern loaders do, or hide the option
  there until then.

### Biome colour noise (a feature: Actinium #108)

- **What it did:** tints vary slightly with world position within a biome (8%
  by default; `a3ad50d6`, closing #56).
- **Actinium's fix:** a hook after blending in common's `BiomeColorCache`,
  applied by the vintage cache, by `MixinBiomeColorHelper` on the uncached
  paths, and by `WorldSlice` outside the cached volume.
- **Upstream:** `BiomeColorHelperMixin` is an `@Overwrite` at priority 1200
  without noise, and common's cache has no hook (`updateColorBuffers` is
  private). Demonica's settings for it are not shown ([Port decisions](#port-decisions)).
- **Why Demonica cannot carry it:** the colour is computed inside an upstream
  `@Overwrite`, which the quarantine does not touch, and inside a private method
  without a hook.
- **Proposal:** a no-op hook after the blur in common's `BiomeColorCache`, and a
  tint hook in the 1.12.2 overwrite.

### The chunk tracker's reconcile (Actinium #113)

- **What went wrong:** chunks that load or unload without going through
  `loadChunk`/`unloadChunk` leave the tracker out of sync with the world, so
  they are never handed to the renderer: holes that approaching does not heal
  (`744df02c`).
- **Actinium's fix:** `ChunkTracker.reconcile` compares the tracked chunks with
  the world's loaded ones and replays the difference, from a RETURN injection on
  `ChunkProviderClient.tick` (`MixinClientChunkManager`).
- **Upstream:** no reconcile; its `ClientChunkManagerMixin` hooks only
  `loadChunk` and `unloadChunk`, and the tracker's `chunkStatus` map is private.
- **Demonica could carry it:** upstream does not hook `tick`, so Demonica can.
  Adding the chunks the tracker missed works through the public, repeat-safe
  `onChunkStatusAdded`; dropping the ones the world unloaded needs the private
  map, which would take a quarantine accessor like S19. Not done.
- **Proposal:** `ChunkTracker.reconcile(LongSet)` upstream, called once per
  client tick.
