# Moving Elevators: the moving cage's floor vanishes under a shader pack

## Symptom

With Moving Elevators 1.4.12 (and SuperMartijn642's Core Lib and Config Lib), the blocks of an elevator cage are
wrong while it moves. Under Demonica with a shader pack the floor is missing: whatever is behind the shaft shows
through where the planks should be. Actinium shows the sky there
([Q-Engineering-Source/Actinium#42](https://github.com/Q-Engineering-Source/Actinium/issues/42)). Without a pack the
cage is brighter than the blocks around it, with either mod and with neither.

## Cause

Moving Elevators draws a moving cage itself: its `LevelRendererMixin` (priority 1000) injects at `@At("HEAD")` of
the four-argument `RenderGlobal.renderBlockLayer` and calls `ElevatorGroupRenderer.renderBlocks`, which sets Forge's
render layer, translates by the camera, fills the `Tessellator` buffer in `DefaultVertexFormats.BLOCK` through
`BlockRendererDispatcher.renderBlock` for each cage block and draws it.

Demonica's S8 seam (`RenderGlobalTerrainMixin`, priority 1100) also injected at HEAD of that method, to enter the
Iris terrain phase of the layer (`ShaderTerrain.beginLayer`). Mixin processes the mixins of a target in priority
order and inserts each HEAD callback before the method's *original* first instruction, so the lower-priority mixin's
callback ends up first: the cage was drawn before Demonica set the phase. Lowering S8's priority would only move the
race to the next mod.

What is bound at that moment decides what the draw hits. `renderSky` ends with the phase at NONE; the shadow pass
then runs inside Demonica's `setupTerrain` wrap, and `DeferredWorldRenderingPipeline.renderShadows` ends with
`beginPass(null)` (program 0) while `ShadowRenderer.restoreGlState` binds Minecraft's own framebuffer. The cage's
pixels landed in the vanilla framebuffer, which Iris's final pass overwrites: the floor is gone. A pack without a
shadow pass keeps the DEFAULT pass bound, so the draw reached the gbuffers there, through `gbuffers_textured`.

The brightness is the mod's own. Vanilla enables the lightmap texture unit only inside the private one-argument
`renderBlockLayer(BlockRenderLayer)`, after that HEAD, so Moving Elevators always draws the cage with the lightmap
off, fullbright.

## Fix

The layer phases are entered at the `renderWorldPass` call site, around the call: `EntityRendererIrisMixin`'s
existing redirect of the four-argument `renderBlockLayer` (every layer, skipped or not) calls
`ShaderTerrain.beginLayer` before it and `endLayer` in a `finally`. Nothing runs before a call, whatever the mixins
on the callee, so the cage is drawn inside the phase, with the gbuffers bound. S8's HEAD/RETURN pair stays as the
fallback for callers outside `renderWorldPass`: GregTech CEu / Lumenized replace the translucent call with
`BloomEffectUtil.renderBloomBlockLayer`, which calls `renderBlockLayer` itself, and dropping the pair would lose the
translucent prelude there. `beginLayer` holds a layer token and returns false to a nested caller, whose `endLayer`
then does nothing; the frame start resets the token should a draw throw
([`docs/celeritas/patches/S8.md`](../celeritas/patches/S8.md#ordering)).

The lightmap is a small gated mixin, `MixinElevatorGroupRenderer` (`mixins.demonica.movingelevators.json`, loaded
when the mod id `movingelevators` is present): `enableLightmap` at HEAD of `ElevatorGroupRenderer.renderBlocks` and
`disableLightmap` at its RETURN. The unit is off at HEAD of `renderBlockLayer` and Celeritas's overwrite enables and
disables it again for its own draw, so the pair is state-neutral. Under a pack the enable reaches the GLSM bridge's
pass match, so the cage takes the pack's terrain program (texture and lightmap, `gbuffers_terrain`) instead of
`gbuffers_textured`.

## Block entities inside a moving cage

Moving Elevators also draws a cage's block entities itself (`ElevatorGroupRenderer.renderBlockEntities`), from a
hook right after vanilla's `TileEntityRendererDispatcher.preDrawBatch()` in `RenderGlobal.renderEntities`, so that
they join the batch `drawBatch` closes. Celeritas cancels `renderEntities` before that call, and the mod's `@Pseudo`
mixin for Vintagium targets `me.jellysquid...SodiumWorldRenderer`, a class Celeritas does not have, so neither hook
ran: a chest or sign in a moving cage disappeared for the ride, with or without a pack, and reappeared when the cage
landed.

Celeritas draws block entities in `CeleritasWorldRenderer.renderBlockEntities`, between its own `preDrawBatch()` and
`drawBatch` calls. The quarantine patch C3 ([`docs/celeritas/patches/C3.md`](../celeritas/patches/C3.md),
`CeleritasWorldRendererCompatMixin`) calls the mod's method right after that `preDrawBatch()`, with the pass's
partial tick, only while the mod is installed (`Mods.MOVINGELEVATORS`, through `MovingElevatorsCompat`). S9 has
entered Iris's block-entity phase at HEAD of the same method, and the mod draws each block entity through
`TileEntityRendererDispatcher.render`, which sets Iris's block-entity id, so under a pack the cage's block entities
take the pack's block-entity program like any other.

## Repro

```
./gradlew runClient -PwithMovingElevators -PdevScript=@scripts/moving-elevators.txt -Dorg.gradle.jvmargs=-Xmx2G
```

`-PwithMovingElevators` loads Moving Elevators 1.4.12 with SuperMartijn642's Core Lib 1.1.25 and Config Lib 1.1.8.
`scripts/moving-elevators.txt` (copy it into `run/client/scripts`) makes a creative world, places two controllers
facing north six blocks apart in the open air with a plank floor in the bottom cage, a chest in it, a stone backdrop
east of the shaft and a roof over it, and shoots the cage at rest and twice mid-ride (a redstone block beside a
controller calls the cage to its floor), without a pack, under BSL and under Complementary Reimagined. Before the
fix the BSL and Complementary mid-ride shots had no floor in the shaft, and the no-pack ones a floor brighter than
the backdrop; after it the planks show at the interpolated height, lit like the terrain around them. The chest was
missing from every mid-ride shot until C3; with it the chest rides with the floor.
