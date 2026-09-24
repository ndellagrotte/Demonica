# S6s: the shadow pass's matrices

Draft, not sent. Replaces `seam.CeleritasWorldRendererShadowMixin` (group SHADOW).

## Problem

`CeleritasWorldRenderer.createChunkRenderMatrices()` returns vanilla's active
render info, the player camera's projection and model-view. Terrain drawn for the
shadow map needs the shadow pass's matrices.

## Proposal

Let the provider supply them while it renders the shadow pass:

```java
protected ChunkRenderMatrices createChunkRenderMatrices() {
    ChunkRenderMatrices shadow = provider != null && provider.isRenderingShadowPass() ? provider.shadowMatrices() : null;
    return shadow != null ? shadow : new ChunkRenderMatrices(ActiveRenderInfoAccessor.getProjectionMatrix(), ...);
}
```

## What Demonica drops

The HEAD-cancel on `createChunkRenderMatrices`. Demonica's matrices come from
Iris's `ShadowRenderer.PROJECTION` and `MODELVIEW` through the `FloatBuffer`
constructor, so no relocated JOML type crosses the seam.
