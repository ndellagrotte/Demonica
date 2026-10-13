package com.demonica.compat.renderlib;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import org.lwjgl.opengl.GL11;

/**
 * Replacement save/restore logic for RenderLib's {@code GLUtil.saveShaderGLState} /
 * {@code restoreShaderGLState}.
 *
 * <p>RenderLib brackets its own shader draws (EntityCulling's occlusion boxes at the head of every
 * {@code RenderGlobal.renderEntities}, its debug render boxes) with a save and restore of the blend,
 * depth, cull and color-mask state, read from the vanilla
 * {@code net.minecraft.client.renderer.GlStateManager} field cache. Under Demonica's GLSM redirector
 * those methods never run, so the field cache stays frozen at its Java initial values: depth test
 * off, depth func {@code GL_LESS}, blend off, cull off. The restore wrote those defaults to the real
 * GL right before the entities drew, so mobs showed through blocks and {@code GL_LESS} leaked into
 * the HUD. This class reads and restores the real GLSM tracked state instead.
 *
 * <p>A single slot, as RenderLib's own static fields are: each save is followed by one restore.
 * The state set is exactly RenderLib's; the color mask is included because EntityCulling clears it
 * for its box draw.
 *
 * <p>Kept outside the mixin package because mixin classes must not expose helper classes that the
 * mixin target (or its callers) reference directly.
 */
public final class RenderLibGlStateSnapshot {

    private static boolean blend;
    private static int blendSrcRgb;
    private static int blendDstRgb;
    private static int blendSrcAlpha;
    private static int blendDstAlpha;
    private static boolean depthTest;
    private static int depthFunc;
    private static boolean depthMask;
    private static boolean cull;
    private static int cullMode;
    private static boolean colorMaskRed;
    private static boolean colorMaskGreen;
    private static boolean colorMaskBlue;
    private static boolean colorMaskAlpha;

    private RenderLibGlStateSnapshot() {
    }

    public static void save() {
        final BlendStateStack blendState = GLStateManager.getBlendState();
        blend = blendState.isEnabled();
        blendSrcRgb = blendState.getSrcRgb();
        blendDstRgb = blendState.getDstRgb();
        blendSrcAlpha = blendState.getSrcAlpha();
        blendDstAlpha = blendState.getDstAlpha();

        depthTest = GLStateManager.getDepthTest().isEnabled();
        depthFunc = GLStateManager.getDepthState().getFunc();
        depthMask = GLStateManager.getDepthState().isMaskEnabled();

        cull = GLStateManager.getCullState().isEnabled();
        cullMode = GLStateManager.glGetInteger(GL11.GL_CULL_FACE_MODE);

        final ColorMaskStack colorMask = GLStateManager.getColorMask();
        colorMaskRed = colorMask.red;
        colorMaskGreen = colorMask.green;
        colorMaskBlue = colorMask.blue;
        colorMaskAlpha = colorMask.alpha;
    }

    public static void restore() {
        if (blend) {
            GLStateManager.enableBlend();
        } else {
            GLStateManager.disableBlend();
        }
        GLStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);

        if (depthTest) {
            GLStateManager.enableDepthTest();
        } else {
            GLStateManager.disableDepthTest();
        }
        GLStateManager.glDepthFunc(depthFunc);
        GLStateManager.glDepthMask(depthMask);

        if (cull) {
            GLStateManager.enableCull();
        } else {
            GLStateManager.disableCull();
        }
        GLStateManager.glCullFace(cullMode);

        GLStateManager.glColorMask(colorMaskRed, colorMaskGreen, colorMaskBlue, colorMaskAlpha);
    }
}
