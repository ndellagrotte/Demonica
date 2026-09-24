# Upstream-PR drafts

One draft per quarantine patch in [`../LEDGER.md`](../LEDGER.md). Each says what
upstream Celeritas would need to change for Demonica to drop the patch. **None
has been sent**; proposing them is the maintainer's decision. Line references
are to upstream commit `06999aabc2`, the pin.

## Most patches are one missing API

The modern loaders wire Iris into Celeritas in-tree:
- `ModernRenderSectionManager` passes `ShaderModBridge.areShadersEnabled()` as
  `hasShadowPass`, answers `isInShadowPass()` from Iris's
  `ShadowRenderingState`, and turns fog occlusion off under shaders.
- `ModernChunkRenderer.useBlockFaceCulling()` is false during the shadow pass.
- `CeleritasWorldRenderer.chooseVertexType()` picks Iris's extended vertex
  format when the pack needs it.

forge122 has none of this: a 1.12 shader mod ships separately, so it cannot be
compiled in. Every decision the modern code makes by calling Iris directly has
to come from something forge122 can query at runtime. One small provider
interface in `common`, registered by the shader mod, would retire S1–S5, S6m,
S6s, S9, S14 and S16, and most of S8:

```java
// A name for upstream to settle; common, next to ShaderModBridge.
public interface TerrainShaderProvider {
    boolean isShaderPackActive();                     // S4, and the gate of the others
    boolean isRenderingShadowPass();                  // S3, S16
    boolean needsShadowPass();                        // S1, read when the section manager is built
    @Nullable ChunkVertexType vertexType(ChunkVertexType celeritasChoice);                // S5
    @Nullable RenderPassConfiguration<?> passConfiguration(ChunkVertexType vertexType);   // S14
    @Nullable GlProgram<? extends ChunkShaderInterface> program(TerrainRenderPass pass,
                                                                RenderPassConfiguration<?> configuration); // S2
    default void beginTerrainLayer(Object layer, float partialTicks) {}                   // S8
    default void endTerrainLayer(Object layer) {}                                         // S8
    default float terrainEyeOffset() { return 0f; }                                       // S6m, S8
    default @Nullable ChunkRenderMatrices shadowMatrices() { return null; }               // S6s
    default void beginBlockEntities() {}                                                  // S9
    default void endBlockEntities() {}                                                    // S9
}
```

Registered with a static setter (for example on `ShaderModBridge`), not a
`ServiceLoader` file. Demonica's jar deliberately carries no
`META-INF/services/org.embeddedt.*` entry, so it can never shadow one of
upstream's own services.

Without a provider registered, every call site keeps today's behaviour, so the
change is inert for users without a shader mod.

The meshing patches (S10, S11, S13) need the same for block data: a
context-aware vertex encoder in `common` (the modern loaders have one in
`modern`), the pack's layer for a block, a fluid material, and the pack's
lighting settings (separate ambient occlusion, no directional shading), used by
forge122's fast block renderer and by the copy of vanilla-path quads.

The remaining drafts are independent small changes: S7 (two members of
`SimpleWorldRenderer`), S15 (fog service selection), S19 (a getter), S20 (a quad
transformer hook), I2 (finish deprecating the `frame` parameters). I1 is a
question for upstream, and S17 needs nothing from upstream.
