package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.terrain.ShaderTerrain;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

/**
 * S6s (docs/celeritas/patches/S6s.md): terrain drawn for the shadow map uses the shadow pass's projection and
 * model-view. Celeritas builds a draw's matrices from vanilla's active render info, which is the player's camera.
 */
@Patch(value = "S6s", group = PatchGroup.SHADOW)
@Mixin(value = CeleritasWorldRenderer.class, remap = false, priority = 1100)
public abstract class CeleritasWorldRendererShadowMixin {
    @Inject(method = "createChunkRenderMatrices", at = @At("HEAD"), cancellable = true)
    private void demonica$shadowMatrices(CallbackInfoReturnable<ChunkRenderMatrices> cir) {
        if (ShaderTerrain.isShadowPass()) {
            cir.setReturnValue(ShaderTerrain.shadowMatrices());
        }
    }
}
