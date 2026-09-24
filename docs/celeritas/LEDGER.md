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
  all 20 lines (a mixin is applied when its target class loads); anything short
  of `n of n` is a moved anchor. MixinExtras applies `@ModifyExpressionValue`,
  `@WrapOperation`, `@WrapWithCondition` and `@WrapMethod` in its own
  transformer extensions, after every config plugin's `postApply`, so the
  plugin holds its lines back until `InjectionAuditExtension`, inserted right
  after MixinExtras's extensions, has seen the finished class. If that
  extension cannot register, or a newer MixinExtras registers after it, the
  lines say how many injectors were "applied later by MixinExtras and not
  checked" instead.
- `AnchorInventoryTest` pins every anchor, upstream's mixin priorities and
  overwrites, and a snapshot of upstream's whole mixin inventory to the pinned
  jar.

## Groups

What a patch's failure costs. The Phase 10 guard (pin check and anchor audit in
`QuarantinePlugin`) turns off a whole group when any of its anchors moved.

| Group | Patches | If the group fails |
|---|---|---|
| BASE | S15 | Terrain is drawn in solid fog colour, with or without a shader pack. |
| CORE_TERRAIN | S2, S5, S6m + S8, S9, S14 | Packs cannot draw terrain. Shaders are turned off with a named reason (L2). |
| SHADOW | S1, S3, S6s, S7, S16, I1, I2 | Shaders without terrain shadows (L1). |
| MESHING | S10, S11, S13 | Packs get no block IDs from terrain: plants do not wave, blocks fall back to the pack's defaults, and water is drawn in the translucent pass instead of the water pass. |
| OPTIONS | O1 | Reese's Sodium Options cannot draw sliders and cycling options, so Video Settings keeps Celeritas's own screen, which still lists Demonica's settings. |
| DEGRADE | S4, S17, S19, S20 | One feature degrades; see the row. |

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
| [S8](patches/S8.md) | `seam.RenderGlobalTerrainMixin` | upstream's `RenderGlobal.renderBlockLayer(BlockRenderLayer, double, int, Entity)` overwrite | HEAD/RETURN: Iris terrain phases and translucent prelude; vanilla's one-argument overload for the translucent layer (Distant Horizons' anchor); `@ModifyArg` of the camera Y to the eye | CORE_TERRAIN, with S6m | not proposed |
| [S9](patches/S9.md) | `seam.CeleritasWorldRendererMixin` | `CeleritasWorldRenderer.renderBlockEntities(TileEntityRenderContext)` | HEAD/RETURN: Iris's block-entity phase | CORE_TERRAIN | not proposed |
| [S10](patches/S10.md) | `seam.ChunkBuilderMeshingTaskMixin` | `ChunkBuilderMeshingTask.execute` (full descriptor): its `canRenderInLayer` and vanilla `renderBlock` calls | In a build for the shader passes: `@WrapOperation`s that render a block the pack moves only in the pack's layer, and attach each vanilla-path block's context (`ShaderBlockContexts`) to the quads it draws | MESHING | not proposed |
| [S11](patches/S11.md) | `seam.VintageChunkBuildContextMixin` | `VintageChunkBuildContext.convertVanillaDataToCeleritasData`, `copyBlockData` | `@WrapOperation`s: the layer's quad contexts go to `copyBlockData`, which prepares the extended vertex encoder with each quad's context; translucent fluid quads go to the pack's water pass | MESHING | not proposed |
| [S13](patches/S13.md) | `seam.ChunkBuilderMeshingTaskMixin`, `seam.VintageBlockRendererMixin` | the `USE_NEW_BLOCK_RENDERER` read in `execute`; `VintageBlockRenderer.renderBlock`, `renderQuadList`, `getVertexLight`, `writeGeometry` | The fast block renderer is Demonica's option (off by default); under a pack it writes each block's context, keeps fluids' material and the pack's layers, and applies the pack's directional-shading and separate-AO settings | MESHING | not proposed |
| [S14](patches/S14.md) | `seam.VintageRenderPassConfigurationBuilderMixin` | `VintageRenderPassConfigurationBuilder.build` | HEAD-cancel while a pack is active: `ShaderPassConfigurations` (separate water pass, semantic and depth-write defines) | CORE_TERRAIN | not proposed |
| [S15](patches/S15.md) | `seam.GLStateManagerFogServiceMixin` | `GLStateManagerFogService` (all 7 getters) | HEAD-cancel: GLSM's fog state, planar shape | BASE | not proposed |
| [S16](patches/S16.md) | `internal.DefaultChunkRendererMixin` | the `useBlockFaceCulling()` call in `DefaultChunkRenderer.render` | `@ModifyExpressionValue`: no face culling in the shadow pass | SHADOW | not proposed |
| [S17](patches/S17.md) | `internal.GlProgramMixin` | `GlProgram.<init>(int, Function)`, `destroyInternal` | Register every linked program with Iris's `DepthColorStorage` | DEGRADE: Iris re-applies its pass after each terrain draw | no upstream change needed; candidate for removal |
| [S19](patches/S19.md) | `internal.ChunkTrackerMixin` | `ChunkTracker.requiredNeighborRadius` | `@Shadow` read behind `ChunkTrackerAccess` | DEGRADE: Distant Horizons' neighbour-radius uniform falls back to its default | not proposed |
| [S20](patches/S20.md) | `seam.VintageBlockRendererQuadsMixin` | the `IBakedModel.getQuads` calls in `VintageBlockRenderer.renderBlock` | `@WrapOperation`: registered `BlockQuadTransformer`s see the quads of each face the fast renderer draws, and the unassigned quads | DEGRADE: addons' transformers do not run | not proposed |
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
| `ParticleManagerCullingMixin` | not ported yet | A feature (bucket C), not shader glue: it comes with the rest of bucket C in Phase 8. It targets vanilla's `ParticleManager` and needs only upstream's public `SimpleWorldRenderer.getLastViewport()`. |
| The fork's `ChunkBuilderMeshingTask`, `VintageChunkBuildContext`, `VintageBlockRenderer` (their shader parts) | S10, S11, S13 | Re-expressed as patches on upstream's classes. The block context is resolved once, in `ShaderBlockContexts`, for both paths; flowing water and lava fall back to the still block's ID when a pack names only `water` or `lava`. |
| The fork's quad contexts (appended only while a block was being drawn) | `QuadContextRecorder` (S12) | One entry per quad, null when drawn without a context, so other code drawing between blocks cannot misalign them. |
| `MixinChunkBuilderMeshingTaskBetterFoliage` | not ported | RLFoliage 2.5.3 ships its own mixin on upstream's `org.taumc` meshing task (a `@Redirect` of `canRenderInLayer`, `@WrapOperation`s on both `renderBlock` calls; checked in its jar). Actinium needed the adapter only because its fork renamed the task. A second adapter would run Better Foliage twice. S10's `@WrapOperation`s compose with it: MixinExtras wraps a redirected call. |
| LittleTiles (`MixinChunkBuilderMeshingTaskLittleTiles`, `MixinTileEntityRenderManager`, `LittleTilesCompat`), `ComponentModelHiderCompat` | Phase 9 | They need mod-gated mixin configs and the mods' APIs, which come with compat in Phase 9. Their hooks on upstream's task and fast renderer go into the quarantine then. |
| The fast renderer's gates (Snow! Real Magic!, ArchitectureCraft, the Component Model Hider, `MissingModelCompat`) | Phase 9 | Ported with compat. Until then `performance.use_fast_block_renderer` stays off by default. |
| `VintageChunkBuildContext.beginVanillaFluidRender` (Fluidlogged API fluids) | not ported | Upstream's own `FluidloggedCompat` draws those fluids, without a context, so they mesh with no block ID. Tagging them needs a hook in upstream's `FluidloggedCompat` (a Phase 9 candidate). |
| The fork renderer's lighting (no quad-normal shading, AO depth blending, no brightness-based quad orientation) | not ported | Renderer choices, not shader glue: upstream's lighting stands. |
| `MixinGuiOptions` (opened RSO from the Video Settings button) | `OptionsScreens`, a `GuiOpenEvent` listener | Upstream's own `MixinGuiOptions` already cancels that button to open its screen; a second cancelling HEAD injection on the same method would race it. Demonica swaps upstream's screen for RSO when it is displayed, with the screen being left as the parent. The mod list's Config button (`@Mod(guiFactory)`, `DemonicaGuiFactory`) goes the same way. |
| The fork's option-API extensions (`Option.getAppliedValue`/`getDefaultValue`/`resetToDefault`/`shouldHideControl`, `OptionImpl.setDefaultValue`, `OptionGroup` names, slider getters, `CyclingControl.getAllowedValues`, `ExternalPage`, `ExternalButtonControl`, `ControlValueFormatter.translateDisabledOrVariable`) | O1 for the slider and cycling getters; the rest without a patch | Undo is `Option.reset()`, and "changed" is `Option.hasChanged()`, which upstream already defines against the applied value. Declared defaults are registered with RSO (`OptionDefaults`, held weakly). No page ever set a group name, so group headers stay empty as before. `ExternalPage` and `ExternalButtonControl` moved into RSO's package; `shouldHideControl` was never read. |
| `ActiniumGameOptionPages` (the fork's copies of the General, Quality and Advanced pages) | `DemonicaOptionPages`, listeners on Celeritas's construction events | Upstream's pages are shown as upstream builds them; Demonica's settings are added into their groups (`OptionGroupConstructionEvent`), at the end of Advanced (`OptionPageConstructionEvent`), and as pages (`OptionGUIConstructionEvent`: Iris's, the Debug page, RSO's own). Both screens get them. Upstream's fast-renderer toggle, which only sets the static flag S13 overrides, is replaced by Demonica's. The fork-only multidraw mode is gone; dynamic FOV waits for its mixin (Phase 8); the biome colour noise settings are not shown, since nothing applies them on upstream (Actinium did in its forked biome colour cache). |
| `ActiniumOptionHost` (pages collected once per game) | `DemonicaOptionHost`, collected for each screen | Celeritas builds its pages for every screen: slider ranges (GUI scale, chunk threads) and tooltips that depend on the window or the shader pack are current. |
| `OptionGUIConstructionBridge` (each listener isolated and rolled back) | one `post`, then drop what RSO cannot show | Upstream's `EventHandlerRegistrar` has no per-listener dispatch. A throwing listener ends the loop; the pages added before it are kept (see O1's draft). |
| RSO's text conversion (the first translation key) | the first key that has a translation | Upstream lists fallback keys for keys 1.12.2 lacks (the default graphics quality's `options.gamma.default`), as its own screen resolves them. |
| The option labels in the fork's `assets/celeritas/lang` | `assets/actinium/lang` | The jar ships no `assets/celeritas`. The keys keep their names; `DemonicaOptionPagesTest` checks every key of Demonica's settings in all three languages. |
