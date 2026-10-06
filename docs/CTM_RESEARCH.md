# CTM research: connected textures on Demonica's stack

Date: 2026-09-29. Tree: `dev` at `9bac5226` (0.7.0-SNAPSHOT; Celeritas pin
`9b661b70`, 2.5.0). This page asks one question — *where should OptiFine-format
connected textures live on the Cleanroom 1.12.2 + Celeritas + Demonica stack,
and what should it be built from?* — and answers it from the source of the two
implementations already on disk, from the pinned Celeritas jar's bytecode, and
from vanilla 1.12.2's own sources.

What was examined, and how:

| Source | What it is | Method |
|---|---|---|
| `.reference/Continuity-1.20.1-dev` | Continuity **3.0.1** for 1.20.1/Fabric (LGPL-3.0), 102 Java files, 8,231 lines | read line by line where cited |
| `~/IdeaProjects/schmaloogium-project/Schmaloogium/reference-src/schlorbium-HD_U_G6_pre1` | decompiled, renamed OptiFine **1.12.2 HD U G6 pre1** ("Schlorbium", build 20210323-161358) — a study workspace, not a buildable mod (its own `DESIGN.md`) | read line by line where cited; obfuscated vanilla names resolved against MCP mappings |
| Celeritas `2.5.0-autobuild.9b661b70` (the pin) | the installed jar | `javap` on `ChunkBuilderMeshingTask`, `WorldSlice`, `RenderGlobalMixin`, `SimpleWorldRenderer`, the mixin config |
| vanilla 1.12.2 (Cleanroom FG3 sources jar in the Gradle cache) | `RenderGlobal`, `World`, `TextureMap`, `BlockRendererDispatcher`, `BlockModelRenderer`, `RenderFallingBlock`, `TileEntityPistonRenderer` | read the extracted sources |
| Demonica | the quarantine, the S20 addon API, the ledger and its drafts | read |

Everything here is static analysis; nothing was compiled or run. Where a claim
rests on bytecode rather than source, the text says so. Appendix A lists the
commands.

## 1. Summary

The findings, by weight:

1. **CTM is a texture feature, not a shader feature, and nothing on this stack
   provides it.** OptiFine cannot run on Cleanroom at all (it is a vanilla
   LaunchWrapper tweaker that replaces 112 vanilla classes wholesale — `files.txt`
   in the Schlorbium decompile). The Chisel-Team CTM mod reads its own JSON
   mcmeta format, not the `mcpatcher/ctm` properties format that every 1.12.2
   resource pack with connected textures uses, and it splits each face into four
   quads (Chisel-Team/ConnectedTexturesMod#53). There is a real gap and a real
   audience: every Celeritas user, with or without shaders (§2).
2. **The render-update plumbing CTM needs already works, on vanilla and on
   Celeritas, with no extra code.** Vanilla's `RenderGlobal.notifyBlockUpdate`
   marks a re-render of the changed block **±1 in every axis**
   (`markBlocksForUpdate(i-1, j-1, k-1, i+1, j+1, k+1, ...)`), and Celeritas
   forwards that range to `SimpleWorldRenderer.scheduleRebuildForBlockArea`,
   which shifts by 4 and re-meshes every section touched. Standard CTM looks no
   further than the 8 face-neighbours, so a block change at a section border
   refreshes the neighbour section's connections for free (§5.1). OptiFine
   relies on exactly this vanilla behaviour.
3. **The default Celeritas meshing path is vanilla and gives CTM everything it
   needs through vanilla classes alone.** `ChunkBuilderMeshingTask.execute` calls
   `BlockRendererDispatcher.renderBlock(state, pos, blockAccess, buffer)` with a
   `CeleritasBlockAccess` that proxies a `WorldSlice` cloned over a
   3×3×3-section neighbourhood (`NEIGHBOR_BLOCK_RADIUS = 2`,
   `NEIGHBOR_CHUNK_RADIUS = 1`). A capture mixin on vanilla
   `BlockModelRenderer.renderModel(IBlockAccess, IBakedModel, IBlockState,
   BlockPos, BufferBuilder, boolean, long)` sees chunk meshing, pistons and
   falling blocks alike. None of this touches a Celeritas class, so **a CTM mod
   needs zero quarantine-style patches and is immune to pin churn** (§5.2).
4. **Demonica already has half an addon API for this, but only half.** S20's
   `com.demonica.api.render.terrain.BlockQuadTransformer` (its Javadoc names CTM
   as the use case) reaches only the fast block renderer, which is **off by
   default** (`performance.use_fast_block_renderer`, S13). The vanilla path —
   the one everyone runs — has no hook (`docs/celeritas/patches/S20.md` says
   so). The API has no consumers yet. Covering the default path from inside
   Demonica would mean a new quarantine patch; covering it from outside needs
   only vanilla mixins (§5.3, §6).
5. **Continuity is portable, and legally.** It is LGPL-3.0 — the license
   Demonica already carries for its Iris and Angelica parts — and its core is
   5,016 lines of almost pure logic (`client/properties` 1,873;
   `client/processor` + `client/model` 3,143) that depends on
   Sprite/BlockState/Direction equivalents, not on Fabric. 1.12.2 is in some
   ways *easier*: `BakedQuad` carries its `TextureAtlasSprite` directly, so
   Continuity's SpriteFinder indirection is unnecessary. The unportable 2,097
   lines (`client/resource`, `mixin`, `mixinterface`, `util`) are the
   Fabric-specific loading and stitching glue, whose 1.12.2 counterparts are
   the clean Forge events `TextureStitchEvent.Pre` and `ModelBakeEvent`
   (§4, §5.4).
6. **The two implementations read different directories, and a 1.12.2 port must
   read the 1.12.2 one.** OptiFine 1.12.2 scans **`mcpatcher/ctm/`**
   (`ResUtils.collectFiles(rp, "mcpatcher/ctm/", ".properties", ...)`); it never
   looks at `optifine/ctm/`, which is the 1.13+ convention. Continuity 3.0.1
   scans only `optifine/ctm`. Packs authored for 1.12.2 (Soartex, Sphax, LB
   Photo Realism, John Smith) put their files under `mcpatcher/ctm`, so a port
   that scans only `optifine/ctm` supports none of them (§3.1, §4).
7. **Integrating CTM into Demonica would reverse the trajectory the project
   just spent four releases on.** `SCOPE_RESEARCH.md` documents cutting
   Actinium's performance features, RSO and the diagnostics jar out of the mod
   jar to keep Demonica a shader pipeline. A Continuity port is ~6,000 lines of
   properties parsing, resource-pack scanning and quad processing that no
   shader pack needs, and that half its audience (Celeritas users without
   Demonica) cannot reach inside Demonica's jar at all (§6, §7).

**Recommendation (§7): build CTM as a standalone Cleanroom mod** — a port of
Continuity's processor/properties core onto 1.12.2 vanilla-path hooks, reading
`mcpatcher/ctm` (and, for packs authored for modern versions, `optifine/ctm`),
LGPL-3.0 so the code moves freely in both directions. Demonica's involvement
stays small and optional: at most, extend the S20-style transformer hook to the
vanilla path behind an API the standalone mod can consume, and in the long run
send the S20 draft's quad-hook proposal upstream so Celeritas itself carries
the seam. The Schlorbium decompile is the behavioural reference for OptiFine
quirks — its `DESIGN.md` already frames it as clean-room study material — while
Continuity is the code source; no code may be copied from the decompile.

## 2. What CTM is, and who is missing it

Connected textures let a resource pack replace a block face's texture with a
tile chosen from the state of the surrounding blocks — the classic glass that
loses its frame where panes meet. The format is MCPatcher's, inherited by
OptiFine: for each effect a `.properties` file in the pack names the blocks or
tiles to match (`matchBlocks`/`matchTiles`), a `method`, and the replacement
`tiles`. A 47-tile method (`ctm`) covers all combinations of the 8 neighbours
of a face; simpler methods use fewer; `random`/`repeat` pick by position. The
full surface OptiFine 1.12.2 parses (verified against `doc/ctm.properties`,
`ConnectedProperties` and `ConnectedParser` in the decompile):

| Property | Values | Notes |
|---|---|---|
| `method` | `ctm`, `ctm_compact`, `horizontal`, `vertical`, `horizontal+vertical`, `vertical+horizontal`, `top`, `random`, `repeat`, `fixed`, `overlay`, `overlay_ctm`, `overlay_random`, `overlay_repeat`, `overlay_fixed` | `renderPass` is documented but marked "Not implemented" by OptiFine's own doc |
| Tiles per method | 47, 5 (+`ctm.<n>` replacements), 4, 4, 7, 7, 1, n, w×h, 1, 17, 47, n, w×h, 1 | |
| `matchBlocks` / `matchTiles` | block ids/names / tile paths | tile entries are checked before block entries, first match wins; files sort alphabetically; `weight` breaks ties; filename inference (`block<id>.properties`) |
| `connect` | `block`, `tile`, `material`, `state` | how a neighbour "connects"; default `block` for block-based files, `tile` for tile-based |
| `connectTiles` / `connectBlocks` | tiles / blocks | overlay methods only |
| `faces`, `metadata`, `biomes`, `heights`/`minHeight`/`maxHeight`, `name` | matchers | `name` matches tile-entity names |
| `tintIndex`, `tintBlock`, `layer` | overlay tinting and layer (`cutout_mipped`, `cutout`, `translucent`) | |
| `innerSeams`, `weights`, `randomLoops`, `symmetry`, `linked`, `width`, `height` | per-method options | |

Who is missing it on this stack:

- **OptiFine is not an option on Cleanroom.** It is not a Forge mod: the jar's
  manifest names a `TweakClass`, and `SchlorbiumClassTransformer` replaces 112
  vanilla classes at load time (`files.txt`, including `BlockModelRenderer`,
  `RenderChunk`, `ChunkRenderDispatcher`, `ViewFrustum`, `RenderGlobal`,
  `BakedQuad`, `ModelBakery`). That is the incompatibility Cleanroom exists to
  escape, and the reason Demonica exists.
- **The Chisel-Team CTM mod (1.12.2-1.0.2.31) is a different product.** It
  reads CTM JSON in texture `.mcmeta` files, not the OptiFine properties
  format, so it does nothing for a resource pack that ships
  `mcpatcher/ctm/*.properties`; its render hook is a coremod plus baked-model
  wrapping; and it splits each face into four quads (its maintainers' own
  analysis of the break/place stutter, issue #53), a 4× quad count that
  Celeritas's encoder would pay for. `RuiXuqi/CTM-Vintage` extends it with
  modern CTM JSON types, still not the OptiFine format.
- **Better Foliage and similar mods patch Celeritas directly** (its jar carries
  a mixin on upstream's meshing task — LEDGER, port decisions), which is
  precedent that mods *may* ship Celeritas mixins, but also a reminder of what
  that costs at every Celeritas release.

So the gap is: OptiFine-format connected textures for players on Cleanroom
1.12.2 running Celeritas — with or without Demonica's shaders. That is a
larger audience than Demonica itself.

## 3. OptiFine 1.12.2: the behavioural spec

Schlorbium is a rename of OptiFine's distributed jar, so its CTM code is the
1.12.2 ground truth: what packs written for this version actually rely on.

### 3.1 Loading and the atlas

`ConnectedTextures.updateIcons(TextureMap)` runs during atlas stitching
(called from OptiFine's `TextureMap` patch): it walks the enabled resource
packs **from last to first** — so the highest-priority pack's properties are
parsed last and land first in the lookup arrays — collecting
`mcpatcher/ctm/**/*.properties`. Each file becomes a `ConnectedProperties`
(908 lines); valid ones register their tiles as atlas sprites through
`cp.updateIcons(textureMap)`, then are indexed twice: into `tileProperties`
by the matched tile's sprite index, and into `blockProperties` by block id.
`multipass` is detected afterwards by intersecting `matchTileIcons` with
`tileIcons`. When the vanilla glass, bookshelf or sandstone textures are
unmodified, a built-in default set of connections is applied
(`getDefaultCtmPaths`) — OptiFine's own "default pack", which Continuity
re-implements as an actual selectable resource pack.

### 3.2 At chunk-bake time

OptiFine replaces `BlockModelRenderer` (and `BakedQuad`, `FaceBakery`, …), and
the patched renderer funnels every quad through
`BlockModelCustomizer.getRenderQuads(quads, world, state, pos, side, layer,
rand, renderEnv)` (82 lines), with a pooled per-rebuild `RenderEnv` carrying
world, position, block id and scratch buffers. For each quad:

- `getConnectedTextureSingle` looks up `tileProperties[quad.sprite.index]`
  then `blockProperties[blockId]`, first match wins;
- the method's neighbour walk runs (`getConnectedTextureCtm` computes the
  8-neighbour bitmask over `BlockDir` tables per face and axis, with special
  axis handling for logs and quartz pillars);
- the tile connection mode (`connect=tile`, `cp.connect == 2`) needs the
  *neighbour's* face sprite, which OptiFine obtains by baking the neighbour
  state's model and reading its first quad (`getNeighbourIcon`) — the expensive
  path, and one a port must cache
  (Continuity's `SpriteCalculator` is that cache, per state and face, with a
  `StampedLock` for mesh threads);
- glass panes get special culling (`skipConnectedTexture` replicates the pane
  connection states so a face against a connecting pane is dropped);
- the sprite swap itself is a cached retexture: `getQuad` keeps an
  `IdentityHashMap` per sprite of source quad → retextured quad, and
  `fixVertex` remaps UVs through `getSpriteU16`/`getInterpolatedU`;
- overlay methods append quads to per-layer overlay lists (`ListQuadsOverlay`)
  and set a flag so the patched `RenderChunk` draws them after the layer;
  emissive variants ride on the patched `BakedQuad.getQuadEmissives()`;
- `renderEnv.isBreakingAnimation` suppresses CTM on the destroy overlay.

### 3.3 What OptiFine's approach costs

Everything above is only possible for OptiFine because it *owns* the renderer:
112 replaced classes, no extension points. None of that machinery is needed to
reproduce the *behaviour* — Continuity is the proof — but the quirks are the
spec: pane handling, multipass, tile-count fallbacks (e.g. `ctm` accepting
fewer than 47 tiles), axis corrections, the reverse pack order. A port should
behave like this code, not be this code: Schlorbium is decompiled,
closed-source OptiFine, and copying from it is not an option; its value is
answering "what does OptiFine do here" while porting Continuity.

## 4. Continuity 3.0.1: the portable implementation

Continuity is a clean-room implementation of the OptiFine CTM, emissive and
block-layers specs for modern Fabric. Its shape (8,231 lines, 102 files; api
206, client 7,738, impl 287):

| Layer | Files / lines | What it does | 1.12.2 port |
|---|---|---|---|
| `client/properties` (15) | 1,873 lines | parse every property, validate tile counts, compare by (pack priority, weight, name) | **port** (~pure logic; `Properties` + block/sprite references) |
| `client/processor` (25) + `client/model` (4) | 3,143 lines | the methods: `SpriteProvider`s (`ctm`, `horizontal`, `vertical`, `top`, `random`, `repeat`, `fixed`, …), connection predicates, direction maps, symmetry, multipass (`PASSES = 4`), overlays; `CtmBakedModel` streams quads through a pushed quad transform; `QuadProcessors` caches state→sprite→processor slices under `StampedLock`s | **port** — the 47-tile `SPRITE_INDEX_MAP`, the `getConnections` walk, `QuadUtil.interpolate` UV remap and the caches are all platform-neutral; 1.12.2's `BakedQuad` exposes its sprite directly, so `RenderUtil.getSpriteFinder()` (Sodium's atlas UV search) is not needed |
| `client/resource` (11), `client/mixin` (12), `client/mixinterface` (3), `client/util` (11) | ~2,100 lines | scanning packs for `optifine/ctm` (see finding 6 — Continuity 3.0.1 scans **only** that path, no `mcpatcher`), injecting sprite ids into `SpriteLoader`/`AtlasLoader` stitching via mixins, wrapping models after bake through Fabric's `ModelLoadingPlugin` (WRAP_LAST_PHASE, blockstate models only), the emissive `_e` suffix map, `optifine/block.properties` custom layers, the glass-pane culling pack through a `RenderLayers` mixin | **rewrite** for 1.12.2's clean hooks: `TextureStitchEvent.Pre` for the atlas, `ModelBakeEvent` (or an `ICustomModelLoader`) for wrapping, pack scanning via the resource manager; the sprite-injection mixins are not needed at all |
| `api` (10) + `impl` (6) | ~490 | `CtmLoaderRegistry`, `QuadProcessor`, `ProcessingDataKey` — an addon surface | port if the standalone mod wants third-party methods (Continuity's own extension point) |

Design choices worth keeping in the port:

- **Wrap models, don't patch the renderer.** `ModelWrappingHandler` wraps only
  the `ModelIdentifier`s that belong to blockstates, so items and other models
  pay nothing; `CtmBakedModel` delegates and falls back to vanilla emission
  when the feature is off, no context exists, or the transform is active
  (nested models).
- **Transform at emission, with caches.** `CtmBakedModel.emitBlockQuads`
  pushes one reusable transform; each quad's sprite selects a processor slice
  from a two-level reference-keyed cache, so a cold world pays the
  properties-matching once per (state, sprite) pair and mesh threads read it
  lock-free. This is the fix for Chisel-CTM's 4×-quad problem: quads are
  retextured, not subdivided.
- **Per-state caches for the expensive lookups.** `SpriteCalculator` (the
  `connect=tile` neighbour-sprite lookup) caches state+face→sprite set under a
  `StampedLock`, seeded with the item-render seed (42) so results are stable.
- **Conformance-first extras.** The built-in "default" pack (glass, sandstone,
  bookshelf, in `resourcepacks/default/.../optifine/ctm/`) and the
  glass-pane culling pack are exactly the two things OptiFine ships as
  built-ins, expressed as ordinary packs.

## 5. The facts that decide the hook

### 5.1 Neighbour data and update propagation already work

- **At bake time**, the mesher's `WorldSlice` clones a
  `3×3×3`-section neighbourhood (`NEIGHBOR_BLOCK_RADIUS = 2`,
  `NEIGHBOR_CHUNK_RADIUS = MathHelper.roundUp(2, 16) >> 4 = 1`,
  `SECTION_LENGTH = 1 + 2·1`, fields read from the pin's bytecode), so reads
  one block outside the section — every CTM neighbour check, including
  diagonals across a section corner — resolve inside the clone. The slice is
  handed to the vanilla path as a `CeleritasBlockAccess`
  (extends `IBlockAccess`), which is what a CTM hook will receive.
- **On updates**, vanilla `RenderGlobal.notifyBlockUpdate` already expands by
  one block in every axis (§1, finding 2), and Celeritas's `RenderGlobalMixin`
  overrides `markBlocksForUpdate` (`func_184385_a`, verified in the jar's
  bytecode) to call `SimpleWorldRenderer.scheduleRebuildForBlockArea`, which
  shifts the range to section coordinates and re-meshes every section touched.
  A block change on a section border therefore refreshes the neighbour
  section's connected textures with no extra code in the CTM mod. This is the
  same vanilla behaviour OptiFine 1.12.2 relies on.

### 5.2 The default path is vanilla and hookable without touching Celeritas

`ChunkBuilderMeshingTask.execute` (pin bytecode, offsets 345–418): per block
and layer it checks `Block.canRenderInLayer`, calls
`ForgeHooksClient.setRenderLayer`, and for `EnumBlockRenderType.MODEL` either
takes the fast renderer (only when `USE_NEW_BLOCK_RENDERER`, false in
production unless Demonica's option turns it on) or calls
`BlockRendererDispatcher.renderBlock(state, pos, slice, bufferBuilder)` —
vanilla's own dispatcher, with the slice as the `IBlockAccess`.
`BlockRendererDispatcher.renderBlock` then calls
`BlockModelRenderer.renderModel(blockAccess, model, state, pos, buffer, true)`
(extracted vanilla source, line 79).

That makes vanilla `BlockModelRenderer.renderModel(IBlockAccess, IBakedModel,
IBlockState, BlockPos, BufferBuilder, boolean, long)` the single capture point
for the whole default path — and it also covers the two auxiliary renderers
Continuity needed mixins for: `RenderFallingBlock` (line 63) and
`TileEntityPistonRenderer` (line 99) both call that same method with a world.
One caveat found while checking: the destroy-stage overlay also reaches this
call — `RenderGlobal.drawBlockDamageTexture` →
`BlockRendererDispatcher.renderBlockDamage` → the same `renderModel` (vanilla
source, lines 38–48) — but Forge re-wraps the model with the crack texture
first, so the port must skip CTM there, as OptiFine does
(`RenderEnv.isBreakingAnimation`): a check on the wrapped model, not a second
hook. One HEAD/RETURN mixin pushes `(blockAccess, state, pos)` into a
thread-local (mesh threads included); wrapped models' `getQuads` consult it.

What a CTM mod therefore needs, and none of it is a Celeritas class:

| Need | 1.12.2 mechanism | Mixin? |
|---|---|---|
| Atlas sprites for CTM tiles | `TextureStitchEvent.Pre` → `map.registerSprite(...)` (fired by `TextureMap.loadSprites` before stitching; verified in the extracted source) | no |
| Wrap blockstate models | `ModelBakeEvent` (`getModelRegistry().put(...)`) or an `ICustomModelLoader`, the Chisel-CTM approach | no |
| World/pos context at quad time | capture on `BlockModelRenderer.renderModel`, thread-local | one vanilla mixin |
| Pack scanning | `SimpleReloadableResourceManager`'s pack list (reflection or one accessor) | no (reflection) |
| Reload | F3+T re-bakes models, re-stitches, re-meshes everything | no |

### 5.3 What Demonica already has, and what it lacks

- `BlockQuadTransformer` (S20) is exactly the right *shape* of API — its
  Javadoc cites CTM — but it is driven by a quarantine mixin on
  `VintageBlockRenderer.renderBlock` only: the fast renderer, off by default,
  and "blocks on the vanilla path never reach the transformers" (S20.md). No
  addon consumes it yet.
- The vanilla-path quads a CTM mod retextures still flow through Demonica's
  shader machinery untouched: S10 resolves the block's context around the
  `renderBlock` call, S12 records one context per drawn quad in the
  `BufferBuilder` in draw order, and S11 carries the contexts into the encoder.
  More quads, or retextured quads, keep the current block's context; nothing in
  a CTM transform can misalign it. Swapped sprites also flow through Iris's
  PBR atlas by name (`_n`/`_s` companions of CTM tiles are found like any
  other sprite), which is a feature packs get for free — and a cost, since a
  47-tile HD set grows the atlas (§8).

### 5.4 The 1.12.2 conveniences

`BakedQuad` exposes `getSprite()`; blockstate→model lookup is
`BlockRendererDispatcher.getModelForState`; the random seed is
`MathHelper.getPositionRandom(pos)` passed down the same path; biome queries
ride `IBlockAccess.getBiome`. The two Fabric problems Continuity's mixins
solve — getting sprites stitched and getting models wrapped — are events on
1.12.2. The one thing 1.12.2 *lacks* is the render-layer override hook
(`RenderLayers` mixin / `optifine/block.properties`): layer changes there mean
`Block.canRenderInLayer`, which the mesher calls per block (§5.2), so custom
block layers would need a mixin on vanilla `Block`/`BlockRendererDispatcher` —
a later phase, not day one.

## 6. Pathways

| | A. Standalone mod | B. Standalone + Demonica API (hybrid) | C. Integrate into Demonica | D. Upstream Celeritas hook | E. Adapt Chisel CTM |
|---|---|---|---|---|---|
| Audience | all Celeritas users | all Celeritas users | Demonica users only | all Celeritas users | all 1.12.2 users |
| New Celeritas patches | 0 (one vanilla mixin) | 0 | 1+ (vanilla-path hook becomes quarantine) | 0 (upstream carries it) | its existing coremod + new Celeritas work |
| Pin churn exposure | none | none | every pin move | upstream's pace | n/a |
| Works without Demonica | yes | yes | no | yes | yes |
| Fast-block-renderer path | not initially | via `BlockQuadTransformer` when Demonica is present (register through the public holder) | via S20 | via the upstream hook | uncertain |
| License shape | LGPL-3.0, Continuity code ports directly | same, plus Demonica-side API stays AGPL | Continuity code into the AGPL jar (precedent exists, but the mod jar grows again) | n/a | MIT, but wrong format and 4× quads |
| Effort to v1 | ~6–7k lines (5k ported + glue) | A + a small Demonica option later | more: option pages, lifecycle, tests in-tree | a Celeritas PR plus waiting on review | a format parser bolted onto an alien architecture; likely a rewrite anyway |
| Risk | overlays' cross-layer emission (below) | same as A | scope creep against the whole 0.3–0.6 arc | timing, upstream appetite | highest |

The overlay wrinkle, because it is the one real gap in pathway A/B: OptiFine's
overlay methods emit quads into a *different layer's* buffer (`layer=`
property). The vanilla-path capture at `BlockModelRenderer` sees only the
buffer of the layer being built; emitting into another layer's builder needs
the meshing build context, which is Celeritas's `VintageChunkBuildContext`
(`getBufferForLayer`). A standalone mod therefore has three choices for
overlays, in rising order of ambition: support overlays only when the target
layer matches the block's current layer; ship a small, clearly-marked mixin on
the Celeritas build context for cross-layer emission (precedent: Better
Foliage and FluxLoading already ship their own Celeritas mixins — LEDGER, port
decisions); or leave cross-layer overlays unimplemented until the upstream
hook (D) exists. Phase it; `overlay` is rare relative to `ctm`/`random`.

Pathway E deserves its rejection spelled out: the Chisel mod's format (JSON in
mcmeta), hook (own coremod), and design (subdivided quads) all fight the goal.
Continuity is the same idea implemented the way this stack wants it.

## 7. Recommendation

**Build pathway B: a standalone Cleanroom mod, with Demonica's role limited to
an optional API bridge.** Concretely:

1. **A new project** (own repo, like S8TNLib's relationship to Demonica — not
   a second jar in Demonica's build, and not version-locked to it): a port of
   Continuity's `properties`/`processor`/`model` core (5,016 lines) plus new
   1.12.2 glue — `mcpatcher/ctm` **and** `optifine/ctm` scanning with
   OptiFine's pack-order semantics (highest-priority pack first, alphabetical,
   tile before block, `weight`), atlas registration via `TextureStitchEvent.Pre`,
   model wrapping via `ModelBakeEvent`, the `BlockModelRenderer` capture
   mixin, a config file, and Continuity's "default" pack as a bundled
   selectable resource pack. LGPL-3.0, crediting Continuity.
2. **Phasing, conformance-first:**
   - *Phase 1:* loader, atlas, wrap, capture; `ctm`, `horizontal`, `vertical`,
     `horizontal+vertical`, `vertical+horizontal`, `top`, `fixed`, `random`,
     `repeat`; `connect=block|state|material`; `faces`, `metadata`,
     `heights`, `name`; the default pack (glass, sandstone, bookshelf).
   - *Phase 2:* `connect=tile` with a per-state/face sprite cache (the
     `SpriteCalculator` port); `ctm_compact` with `ctm.<n>`; multipass;
     `weights`, `symmetry`, `linked`, `randomLoops`, `innerSeams`, `biomes`;
     the pane behaviours.
   - *Phase 3:* the overlay family, same-layer first, then the cross-layer
     decision (§6); `tintIndex`/`tintBlock`.
   - *Phase 4:* emissive `_e` (duplicate quads with an emissive sprite at the
     same hook), `optifine/block.properties` custom layers, the glass-pane
     culling pack.
   - Each phase validated the way this repository already works: a conformance
     resource pack exercising every method, screenshot-diffed, plus a run under
     BSL/Complementary to check the Iris interactions (PBR companions,
     renderStage, pack layer moves).
3. **Demonica-side (small, optional, later):** if the standalone mod wants to
   cover the fast block renderer, it can already register a
   `BlockQuadTransformer` through the public holder when Demonica is detected —
   no Demonica change needed. If the vanilla path should also go through that
   API, that is a new seam patch (an S20 sibling on the vanilla path) and
   should follow the S20 draft's real proposal: **ask Celeritas upstream for
   the quad-transformer hook** (pathway D), which would eventually retire the
   standalone mod's one vanilla mixin too, the same way the fog and provider
   drafts would retire S-series patches.
4. **Use Schmaloogium as the oracle, not the source.** When a pack behaves
   differently under OptiFine than under the port, the decompile answers what
   1.12.2 OptiFine actually does (pane culling, tile-count fallbacks, axis
   fixes, multipass detection). Copying its code is off the table; Continuity
   is the code source and its wiki is the spec of record for the modern
   extensions.

Why not C, stated plainly: Demonica has spent 0.3.0 through 0.6.0 removing
everything from the jar that a shader pack does not need, and CTM is exactly
that — a texture feature whose primary audience includes players who will
never install a shader. Keeping it out also keeps the AGPL jar from gaining an
LGPL subsystem with an independent release cadence, and keeps the mod useful
on Celeritas-only packs, which is where the 1.12.2 audience actually is.

## 8. Risks and open questions

- **Atlas size.** A 47-tile set at HD resolutions can push the 1.12.2 block
  atlas toward `GL_MAX_TEXTURE_SIZE` (vanilla's `Stitcher` already sizes to
  `Minecraft.getGLMaximumTextureSize()`), and every tile is also a candidate
  for `_n`/`_s` PBR companions under Demonica, doubling or tripling the cost.
  Mitigation: log the atlas size at reload; ship the default pack at 16×16;
  document the knob (mipmaps).
- **Thread-safety.** Meshing runs on Celeritas's worker threads; every cache
  the port keeps must be ConcurrentMaps or `StampedLock`-guarded, and the
  world/pos capture must be a thread-local (Continuity's caches are the
  pattern; Celeritas's mesher defaults to parallel builds).
- **Extended block states.** `BlockModelRenderer` hands models the block's
  extended state; the wrapper must pass it through untouched and fall back to
  vanilla quads when the thread-local is empty (items, GUIs, mods drawing
  models directly) — Continuity's `isVanillaAdapter`/fallback handling is the
  reference.
- **Pack-order semantics.** OptiFine iterates packs last-to-first and tile
  entries before block entries; Continuity assigns ascending priorities and
   sorts descending. The port must pick one and state it; getting it wrong
   silently reorders packs' overrides.
- **Cleanroom event compatibility.** `TextureStitchEvent.Pre` and
   `ModelBakeEvent` are Forge events that Cleanroom preserves (the vanilla
   sources carry the Forge hooks, and Demonica uses Forge events already),
   but a day-one spike should confirm both fire on Cleanroom's reload thread.
- **Open:** does the standalone mod ship its own options GUI, or add to
  Celeritas's Video Settings the way Demonica does? Not needed for Phase 1
  (a config file suffices). Where exactly the mod should live (repo name,
  whether S8TNLib-style tooling is reused) is a maintainer decision.

## Appendix A — how each claim was checked

| Claim | Where |
|---|---|
| OptiFine scans `mcpatcher/ctm` only; default connections; multipass detection; reverse pack order | `net/schlorbium/ConnectedTextures.java` (`updateIcons`, `getDefaultCtmPaths`, `detectMultipass`, `getConnectedTextureSingle`, `getQuad`/`fixVertex`, `getNeighbourIcon`, `skipConnectedTexture`) |
| OptiFine properties surface; method list; tile counts | `doc/ctm.properties` (353 lines), `ConnectedProperties.java`, `config/ConnectedParser.java` |
| OptiFine replaces 112 vanilla classes | `files.txt`; patch deltas in `patch/*.xdelta` |
| Continuity architecture; `SPRITE_INDEX_MAP`; `PASSES = 4`; caches; wrapping; atlas injection; `optifine/ctm`-only scanning; built-in packs; LGPL-3.0 | `.reference/Continuity-1.20.1-dev/src/main/java/...`: `CtmBakedModel`, `ModelWrappingHandler`, `QuadProcessors`, `SimpleQuadProcessor`, `CtmSpriteProvider`, `SpriteCalculator`, `CtmPropertiesLoader`, `SpriteLoaderMixin`, `AtlasLoaderMixin`, `ContinuityClient`, `LICENSE`; line counts by `find -name '*.java' -exec wc -l` |
| Celeritas vanilla path calls `BlockRendererDispatcher.renderBlock`; fast-renderer switch; slice radius; update forwarding | `javap -c -p` on the pin jar's `ChunkBuilderMeshingTask`, `WorldSlice`, `RenderGlobalMixin` (`func_184385_a` = `markBlocksForUpdate`), `SimpleWorldRenderer.scheduleRebuildForBlockArea`; `methods.csv` for the SRG name |
| Vanilla ±1 render-update expansion; `TextureStitchEvent.Pre` timing; dispatcher/piston/falling-block/damage call chains | extracted Cleanroom FG3 1.12.2 sources jar: `RenderGlobal.java:2362-2369`, `TextureMap.loadSprites`, `BlockRendererDispatcher.java:38-48,51,79`, `BlockModelRenderer.java:36-53` (the 6-arg delegates to the 7-arg, which dispatches virtually), `TileEntityPistonRenderer.java:97-99`, `RenderFallingBlock.java:63`, `RenderGlobal.drawBlockDamageTexture`; `ForgeBlockModelRenderer` (overrides only `renderModelFlat`/`renderModelSmooth`, so the 7-arg `renderModel` is the shared entry) |
| S20 API scope; no consumers; fast renderer off by default; S12/S10/S11 quad contexts | `com/demonica/api/render/terrain/BlockQuadTransformer*.java`, `VintageBlockRendererQuadsMixin.java`, `docs/celeritas/patches/S20.md`, `S13.md`, `VintageChunkBuildContextMixin.java`, LEDGER |
| Chisel CTM format/design; 4× quads; coremod | Chisel-Team/ConnectedTexturesMod README, wiki and issue #53 (web, 2026-09-29) |

*Schlorbium note:* the decompile is a rebrand of OptiFine's distributed jar
sitting in a separate study workspace (`DESIGN.md` there: "not a buildable mod
project… a reverse-engineering workspace"). It informed behaviour claims only;
no code was or should be taken from it.
