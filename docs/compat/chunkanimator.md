# Chunk Animator

Chunk Animator (CurseForge project 236484, file 3850023, 1.12.2-1.2.1, mod id
`chunkanimator`) is an ASM coremod. It hooks vanilla's `RenderChunk.setOrigin`
(`func_189562_a`) to record a chunk's animation start and
`ChunkRenderContainer.preRenderChunk` (`func_178003_a`) to translate the chunk
while it is drawn. Celeritas draws no `RenderChunk` and never calls
`preRenderChunk`, so with Demonica alone the mod loaded and nothing animated.

Actinium PR #44 bridged the mod into its vendored renderer. Demonica forked after
that PR but dropped the bridge when it moved to upstream Celeritas, which has no
chunk animation API ([the ledger's port decisions](../celeritas/LEDGER.md#port-decisions)).
Since 0.7.3 the bridge is back as quarantine patch C2
([`C2.md`](../celeritas/patches/C2.md)).

## How it works

| Chunk Animator's hook | Demonica's counterpart | Where |
|---|---|---|
| `RenderChunk.setOrigin` | `ChunkAnimationProvider.onSectionAdded(RenderSection)` | `RenderSectionManager.onSectionAdded`, TAIL (client thread, when a section enters the render distance) |
| `ChunkRenderContainer.preRenderChunk` | `ChunkAnimationProvider.getSectionOffset(RenderSection, float[3])` | the `BatchAssembler.createCachedBatch` call in `DefaultChunkRenderer.render` (render thread, when a region's batch is rebuilt) |

`ChunkAnimatorCompat` (`com.demonica.compat.chunkanimator`) implements the
provider over Chunk Animator's public `AnimationHandler`, whose `setOrigin` and
`getOffset` take plain `Object` tokens: the `RenderSection` is the token. The
handler keys its state by identity in a `WeakHashMap` and removes an entry when
its animation has run, so no cleanup is needed. `Demonica.onInit` installs the
provider behind `Mods.CHUNKANIMATOR`; without the mod, no Chunk Animator class is
loaded and the two quarantine mixins find no provider and change nothing.

**Why not Actinium's draw split as it was.** Actinium's fork filled each region's
draw commands every frame, so it could leave animating sections out of the batch
and draw them afterwards with an individual emitter. Upstream Celeritas at the
pin caches each region's multi-draw batch (`CachedBatch`) and rebuilds it only
when the visible section list changes. C2 wraps that rebuild: when any section
of the region reports an offset, upstream builds the cached batch from a render
list of the static sections, and `AnimatedSectionRenderer` draws each animating
section with its own one-section batch from `BatchAssembler.fillRegion`, the
region offset plus the animation offset as `u_RegionOffset`. The cached batch
remembers the shorter section list, so upstream's validity check fails on the
next frame and the split is redone until nothing animates; then the full batch
is cached again and the region costs nothing extra. The split applies to Iris's
shadow pass as well (the offset goes through the pack's
`IrisCeleritasChunkShaderInterface.setRegionOffset`), as it did in Actinium.

Differences from vanilla, as in Actinium: a section's clock starts when it is
created (enters the render distance) rather than when a `RenderChunk` is
repositioned, and the first `getOffset` starts the animation, so a section that
is built long after it was created animates when first drawn. Animation mode,
easing, duration and "disable around player" are Chunk Animator's own settings.

## Dev runs

`-PwithCompatMods` loads the mod. Its `MCPNames` detects a deobfuscated
workspace and reads `./../mcp/methods.csv` and `./../mcp/fields.csv` relative
to the client's working directory, `run/client`; without them its class
transformer fails at class init (`ExceptionInInitializerError`). The
`prepareChunkAnimatorMcpMappings` task, which `runClient` depends on under
that property, unpacks the two CSVs from the `de.oceanlabs.mcp:mcp_stable:39-1.12`
zip (resolved from Cleanroom's Maven) into `run/mcp/`. The mod itself runs as
its raw SRG jar (`devRuntimeMods`), which Cleanroom remaps as it loads it.
`scripts/chunkanimator.txt` is the scene: a fresh world, then a teleport of 96
blocks so new sections enter the render distance; the shots a few ticks after it
show the terrain below the player rising into place.
