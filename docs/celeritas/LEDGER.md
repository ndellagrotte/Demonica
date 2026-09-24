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
  their targets`. A dev run lists all 14 lines; anything short of `n of n` is
  a moved anchor. MixinExtras applies `@ModifyExpressionValue`,
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
| DEGRADE | S4, S17, S19 | One feature degrades; see the row. |

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
| [S14](patches/S14.md) | `seam.VintageRenderPassConfigurationBuilderMixin` | `VintageRenderPassConfigurationBuilder.build` | HEAD-cancel while a pack is active: `ShaderPassConfigurations` (separate water pass, semantic and depth-write defines) | CORE_TERRAIN | not proposed |
| [S15](patches/S15.md) | `seam.GLStateManagerFogServiceMixin` | `GLStateManagerFogService` (all 7 getters) | HEAD-cancel: GLSM's fog state, planar shape | BASE | not proposed |
| [S16](patches/S16.md) | `internal.DefaultChunkRendererMixin` | the `useBlockFaceCulling()` call in `DefaultChunkRenderer.render` | `@ModifyExpressionValue`: no face culling in the shadow pass | SHADOW | not proposed |
| [S17](patches/S17.md) | `internal.GlProgramMixin` | `GlProgram.<init>(int, Function)`, `destroyInternal` | Register every linked program with Iris's `DepthColorStorage` | DEGRADE: Iris re-applies its pass after each terrain draw | no upstream change needed; candidate for removal |
| [S19](patches/S19.md) | `internal.ChunkTrackerMixin` | `ChunkTracker.requiredNeighborRadius` | `@Shadow` read behind `ChunkTrackerAccess` | DEGRADE: Distant Horizons' neighbour-radius uniform falls back to its default | not proposed |
| [I1](patches/I1.md) | `seam.VintageRenderSectionManagerShadowMixin` | `VintageRenderSectionManager.getAsyncOcclusionMode` | RETURN: "Everything" becomes "Only Shadows" when the manager gets a shadow pass | SHADOW | a question for upstream |
| [I2](patches/I2.md) | `internal.SimpleWorldRendererMixin` | `SimpleWorldRenderer.setupTerrain`, `setupShadowTerrain` | `@ModifyVariable(HEAD)` of `frame` to `DemonicaFrameClock.next()` | SHADOW | not proposed |

S18 is not a patch: the shadow pass's block entities come from the public
`SimpleWorldRenderer.forEachVisibleBlockEntity` (see S7).

Verified in the dev client on 2026-09-23: all 14 mixins apply, and all 26 of
their injectors find their targets, S16's `@ModifyExpressionValue` among them
(checked after MixinExtras applied it). With S16's anchor pointed at a method
that does not exist, the same run warns that the injector found its target in
0 of 1 methods, and the game runs on. The bytecode exported with
`-PmixinExport` shows S16's injection in `DefaultChunkRenderer.render`, S8's
`@ModifyArg` before `drawChunkLayer`, and I2's `@ModifyVariable` at the head of
both searches.

## Port decisions

Actinium classes that changed shape on the way to upstream Celeritas.

| Actinium | Demonica | Why |
|---|---|---|
| `GlProgramIrisMixin` (on the fork's `GlProgram`) | S17 | Upstream's `GlProgram` is a Celeritas class, so the mixin moved into the quarantine. |
| `ParticleManagerCullingMixin` | not ported yet | A feature (bucket C), not shader glue: it comes with the rest of bucket C in Phase 8. It targets vanilla's `ParticleManager` and needs only upstream's public `SimpleWorldRenderer.getLastViewport()`. |
