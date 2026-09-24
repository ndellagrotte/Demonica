package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.terrain.ShaderTerrain;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.VintageRenderSectionManager;

/**
 * S4 (docs/celeritas/patches/S4.md): fog occlusion culls sections hidden by vanilla fog. A shader pack that turns
 * vanilla fog off draws its own, usually farther out, so sections the fog distance would cull stay visible. The rule
 * is the same for the terrain and shadow searches.
 */
@Mixin(value = VintageRenderSectionManager.class, remap = false, priority = 1100)
public abstract class VintageRenderSectionManagerMixin {
    @Inject(method = "useFogOcclusion", at = @At("HEAD"), cancellable = true)
    private void demonica$noFogOcclusionWithoutVanillaFog(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderTerrain.isPackActive() && ChunkShaderFogComponent.FOG_SERVICE.getFogMode() == ChunkFogMode.NONE) {
            cir.setReturnValue(false);
        }
    }
}
