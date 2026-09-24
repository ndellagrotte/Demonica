# S6m: an eye-anchored model-view for terrain under a pack

Draft, not sent. Replaces the `createChunkRenderMatrices` injection in
`seam.CeleritasWorldRendererMixin` (group CORE_TERRAIN, together with S8).

## Problem

forge122 draws terrain relative to the view entity's feet, which is the origin
of vanilla 1.12's model-view. Shader packs reconstruct positions from
`gbufferModelView`, whose origin is the eye. Under a pack, the draw has to be
relative to the eye (S8 moves the camera up by the eye height) and its model-view
translated by the same height. The two shifts have to apply together: either one
alone moves the world by the eye height.

## Proposal

Part of [S8](S8.md): the provider's `terrainEyeOffset()` (0 without a pack) is added
to the camera Y in `renderBlockLayer`, and `createChunkRenderMatrices()`
translates the model-view by the same amount:

```java
protected ChunkRenderMatrices createChunkRenderMatrices() {
    float eye = provider != null ? provider.terrainEyeOffset() : 0f;
    Matrix4f modelView = ActiveRenderInfoAccessor.getModelViewMatrix();
    if (eye != 0f) modelView = new Matrix4f(modelView).translate(0f, eye, 0f);
    return new ChunkRenderMatrices(ActiveRenderInfoAccessor.getProjectionMatrix(), modelView);
}
```

## What Demonica drops

The HEAD-cancel on `createChunkRenderMatrices`, and the hand-off between the two
patches (`ShaderTerrain.pendingEyeHeight`), which exists only so that one patch
missing disables the other.
