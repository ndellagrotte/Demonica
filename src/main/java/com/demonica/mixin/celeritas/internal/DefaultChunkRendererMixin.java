package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.terrain.ShaderTerrain;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * S16 (docs/celeritas/patches/S16.md): no block face culling in the shadow pass. Celeritas skips the faces of a
 * section that point away from the camera, and in the shadow pass that is still the player's camera, while shadows
 * are cast by faces that point away from the player as often as not. Upstream's modern loaders turn it off during
 * Iris's shadow pass the same way.
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false, priority = 1100)
public abstract class DefaultChunkRendererMixin {
    @ModifyExpressionValue(
        method = "render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;"
            + "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
        at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;useBlockFaceCulling()Z")
    )
    private boolean demonica$noFaceCullingForShadows(boolean useBlockFaceCulling) {
        return useBlockFaceCulling && !ShaderTerrain.isShadowPass();
    }
}
