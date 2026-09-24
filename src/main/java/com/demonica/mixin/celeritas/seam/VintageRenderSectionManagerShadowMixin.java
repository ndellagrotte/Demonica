package com.demonica.mixin.celeritas.seam;

import com.demonica.celeritas.terrain.ShaderTerrain;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.render.terrain.VintageRenderSectionManager;

/**
 * The shadow pass of Celeritas's 1.12.2 section manager:
 * <ul>
 *   <li>S3 (docs/celeritas/patches/S3.md): while Iris draws the shadow map, the manager is in its shadow pass, so the
 *   shadow render lists, rebuild queues and visibility answer. forge122's manager inherits {@code isInShadowPass()}
 *   from RenderSectionManager, which is always false; this adds the override.</li>
 *   <li>I1 (docs/celeritas/patches/I1.md): with a shadow pass, the terrain search stays synchronous: "everything"
 *   asynchronous occlusion becomes "only shadows".</li>
 * </ul>
 */
@Mixin(value = VintageRenderSectionManager.class, remap = false, priority = 1100)
public abstract class VintageRenderSectionManagerShadowMixin {
    // Without a shadow pass there are no shadow lists to switch to (S1 may be missing, or the manager predates the
    // pack), so the manager stays in its terrain pass.
    public boolean isInShadowPass() {
        return ((RenderSectionManager) (Object) this).hasShadowPass() && ShaderTerrain.isShadowPass();
    }

    // Read once, by RenderSectionManager's constructor, when S1 has just decided on the shadow pass.
    @Inject(method = "getAsyncOcclusionMode", at = @At("RETURN"), cancellable = true)
    private void demonica$synchronousTerrainSearch(CallbackInfoReturnable<AsyncOcclusionMode> cir) {
        if (cir.getReturnValue() == AsyncOcclusionMode.EVERYTHING && ShaderTerrain.needsShadowPass()) {
            cir.setReturnValue(AsyncOcclusionMode.ONLY_SHADOW);
        }
    }
}
