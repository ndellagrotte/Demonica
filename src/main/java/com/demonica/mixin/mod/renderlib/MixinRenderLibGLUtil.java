package com.demonica.mixin.mod.renderlib;

import com.demonica.compat.renderlib.RenderLibGlStateSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Compatibility bridge for RenderLib's {@code GLUtil.saveShaderGLState} / {@code restoreShaderGLState}.
 *
 * <p>RenderLib saves the GL state around its own shader draws by reading the vanilla
 * {@code net.minecraft.client.renderer.GlStateManager} field cache. Under Demonica's GLSM redirector
 * those methods never run, so the field cache stays frozen at its Java initial values (depth test off,
 * depth func {@code GL_LESS}, blend off, cull off). EntityCulling (meldexun) calls the pair at the head of
 * every {@code RenderGlobal.renderEntities} for its OpenGL-based occlusion culling, so the restore wrote
 * those defaults to the real GL right before the entities drew: mobs through blocks, garbled models and a
 * leaked {@code GL_LESS} (issues #21 and #22). This bridge reads and restores the real GLSM tracked state
 * instead (see {@link RenderLibGlStateSnapshot}). Loaded only when RenderLib is present (see MixinLate).
 *
 * <p>Targeted by name: both methods take no parameters, so nothing here needs RenderLib on the compile
 * classpath.
 */
@Mixin(targets = "meldexun.renderlib.util.GLUtil", remap = false)
public abstract class MixinRenderLibGLUtil {

    /**
     * @author Demonica
     * @reason Save the real GLSM tracked state instead of the frozen vanilla field cache.
     */
    @Overwrite
    public static void saveShaderGLState() {
        RenderLibGlStateSnapshot.save();
    }

    /**
     * @author Demonica
     * @reason Restore the real GLSM tracked state instead of the frozen vanilla field cache.
     */
    @Overwrite
    public static void restoreShaderGLState() {
        RenderLibGlStateSnapshot.restore();
    }
}
