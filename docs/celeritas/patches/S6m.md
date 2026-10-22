# S6m: an origin-anchored model-view for terrain under a pack

Draft, not sent. Replaces the `createChunkRenderMatrices` injection in
`seam.CeleritasWorldRendererMixin` (group CORE_TERRAIN, together with S8).

## Problem

forge122 draws terrain relative to the view entity's feet, which is the origin
of vanilla 1.12's model-view. Shader packs reconstruct world positions from
`gbufferModelView` and `cameraPosition`, whose origin is the camera's render
origin (`Camera.getOrigin()`, relative to the feet): the eye in first person,
and in third person the real camera, the view-space origin of vanilla's
model-view, as on modern Iris. Under a pack, the draw has to be relative to
that origin (S8 adds it to the camera on x, y and z) and its model-view
translated by the same offset. The two shifts have to apply together: either
one alone moves the world by the offset.

## Proposal

Part of [S8](S8.md): the provider's `terrainOrigin()` (null without a pack) is
added to the camera passed to `drawChunkLayer` in `renderBlockLayer`, and
`createChunkRenderMatrices()` translates the model-view by the same vector:

```java
protected ChunkRenderMatrices createChunkRenderMatrices() {
    Vector3fc origin = provider != null ? provider.terrainOrigin() : null;
    Matrix4f modelView = ActiveRenderInfoAccessor.getModelViewMatrix();
    if (origin != null) modelView = new Matrix4f(modelView).translate(origin);
    return new ChunkRenderMatrices(ActiveRenderInfoAccessor.getProjectionMatrix(), modelView);
}
```

## What Demonica drops

The HEAD-cancel on `createChunkRenderMatrices`, and the hand-off between the two
patches (`ShaderTerrain.PENDING_ORIGIN` and `originPending`, set by S8's
`@ModifyArgs` and taken by the next `createChunkRenderMatrices`, the first call
in `drawChunkLayer`), which exists only so that one patch missing disables the
other.
