package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.terrain.ShaderTerrain;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

/**
 * The shader-pack hooks of Celeritas's 1.12.2 world renderer:
 * <ul>
 *   <li>S5 (docs/celeritas/patches/S5.md): sections are meshed in the vertex format the pack needs.</li>
 *   <li>S6m (docs/celeritas/patches/S6m.md): terrain drawn relative to the eye gets an eye-anchored model-view.</li>
 *   <li>S9 (docs/celeritas/patches/S9.md): block entities are drawn in Iris's block-entity phase.</li>
 * </ul>
 */
@Mixin(value = CeleritasWorldRenderer.class, remap = false, priority = 1100)
public abstract class CeleritasWorldRendererMixin {
    @Inject(method = "chooseVertexType", at = @At("HEAD"), cancellable = true)
    private void demonica$packVertexType(CallbackInfoReturnable<ChunkVertexType> cir) {
        ChunkVertexType type = ShaderTerrain.packVertexType();
        if (type != null) {
            cir.setReturnValue(type);
        }
    }

    // Takes effect only for the draw that S8 moved to the eye, so the two shifts apply together or not at all.
    @Inject(method = "createChunkRenderMatrices", at = @At("HEAD"), cancellable = true)
    private void demonica$eyeMatrices(CallbackInfoReturnable<ChunkRenderMatrices> cir) {
        ChunkRenderMatrices matrices = ShaderTerrain.takeEyeMatrices();
        if (matrices != null) {
            cir.setReturnValue(matrices);
        }
    }

    // The full descriptor: the erased bridge renderBlockEntities(Object) calls this method, and injecting into both
    // would enter the phase twice.
    @Inject(method = "renderBlockEntities(Lorg/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer$TileEntityRenderContext;)I",
        at = @At("HEAD"))
    private void demonica$beginBlockEntities(CeleritasWorldRenderer.TileEntityRenderContext context, CallbackInfoReturnable<Integer> cir) {
        if (Iris.enabled) {
            GbufferPrograms.beginBlockEntities();
            GbufferPrograms.setBlockEntityDefaults();
        }
    }

    @Inject(method = "renderBlockEntities(Lorg/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer$TileEntityRenderContext;)I",
        at = @At("RETURN"))
    private void demonica$endBlockEntities(CeleritasWorldRenderer.TileEntityRenderContext context, CallbackInfoReturnable<Integer> cir) {
        if (Iris.enabled) {
            GbufferPrograms.endBlockEntities();
        }
    }
}
