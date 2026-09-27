package com.demonica.diagnostics.mixin;

import com.demonica.diagnostics.iris.IrisGlDiagnostics;
import com.demonica.render.RenderWorldRecursionGuard;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world pass's timing table and stage markers (IrisGlDiagnostics): a marker or a timed stage after each step of
 * {@code renderWorldPass} and {@code updateCameraAndRender}. Demonica's EntityRendererIrisMixin does the rendering
 * work at the same points and marks the stages inside it.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererDiagnosticsMixin {
    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void demonica$beginWorldPassTiming(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!RenderWorldRecursionGuard.isNested()) {
            IrisGlDiagnostics.beginWorldPassTiming(pass);
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void demonica$finishWorldPassTiming(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!RenderWorldRecursionGuard.isNested()) {
            IrisGlDiagnostics.finishWorldPassTiming();
        }
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderEndNanoTime:J",
            opcode = Opcodes.PUTFIELD,
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    // Somnia rewrites the renderWorld call in updateCameraAndRender to SomniaUtil.renderWorld,
    // so anchor this stage to the first field write that follows the world render instead.
    private void demonica$checkAfterRenderWorld(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("entity-renderer:after-render-world");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntityOutlineFramebuffer()V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterEntityOutlineFramebuffer(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("entity-renderer:after-entity-outline-fbo");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/ShaderGroup;render(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterShaderGroup(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("entity-renderer:after-shader-group");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterGameOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("entity-renderer:after-game-overlay");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", shift = At.Shift.AFTER, remap = false)
    )
    private void demonica$checkAfterDrawScreen(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("entity-renderer:after-draw-screen");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterClear(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("clear-to-camera");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterCameraTransform(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("camera-to-active-render-info");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterActiveRenderInfo(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("active-render-info-to-frustum");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterClippingHelper(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("clipping-helper-to-culling");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ICamera;setPosition(DDD)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterFrustumPosition(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("culling-to-sky-fog");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupFog(IF)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyFog(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("sky-fog-to-projection");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V")
    )
    private void demonica$checkBeforeSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("render-sky-call");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;matrixMode(I)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyProjectionMatrixMode(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("sky-matrix-mode-to-load-identity");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;loadIdentity()V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyProjectionLoadIdentity(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("sky-load-identity-to-fov");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getFOVModifier(FZ)F", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyFov(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("sky-fov-to-perspective");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyPerspective(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.recordWorldPassStage("sky-perspective-to-modelview");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-sky");
        IrisGlDiagnostics.recordWorldPassStage("render-sky-to-clouds");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;FIDDD)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterClouds(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (Iris.enabled && IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            IrisGlDiagnostics.recordWorldPassStage("cloud-check-to-setup-terrain");
            return;
        }

        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-clouds");
        IrisGlDiagnostics.recordWorldPassStage("clouds-to-setup-terrain");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-setup-terrain");
        IrisGlDiagnostics.recordWorldPassStage("setup-terrain-to-update-chunks");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;updateChunks(J)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterUpdateChunks(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-update-chunks");
        IrisGlDiagnostics.recordWorldPassStage("update-chunks-to-terrain-solid");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void demonica$checkAfterSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-terrain-solid");
        IrisGlDiagnostics.recordWorldPassStage("terrain-solid-to-cutout-mipped");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 1)
    )
    private void demonica$checkAfterCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-terrain-cutout-mipped");
        IrisGlDiagnostics.recordWorldPassStage("terrain-cutout-mipped-to-cutout");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 2)
    )
    private void demonica$checkAfterCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-terrain-cutout");
        IrisGlDiagnostics.recordWorldPassStage("terrain-cutout-to-entities-0");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void demonica$checkAfterEntitiesPass0(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.check("render-world-pass:" + pass + ":after-entities-0");
        IrisGlDiagnostics.recordWorldPassStage("entities-0-to-selection-box");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;IF)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSelectionBox(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-selection-box");
        IrisGlDiagnostics.recordWorldPassStage("selection-box-to-block-damage");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-block-damage");
        IrisGlDiagnostics.recordWorldPassStage("block-damage-to-lit-particles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-lit-particles");
        IrisGlDiagnostics.recordWorldPassStage("lit-particles-to-particles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-particles");
        IrisGlDiagnostics.recordWorldPassStage("particles-to-weather");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-weather");
        IrisGlDiagnostics.recordWorldPassStage("weather-to-terrain-translucent");
    }

    // GTCEu's GregTechTransformer rewrites renderWorldPass via ASM and replaces the 4th
    // renderBlockLayer call (TRANSLUCENT) with BloomEffectUtil.renderBloomBlockLayer, so this
    // ordinal no longer exists under GregTech. Debug-stage marker only; tolerate its absence.
    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 3),
        require = 0
    )
    private void demonica$checkAfterTranslucentTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-terrain-translucent");
        IrisGlDiagnostics.recordWorldPassStage("terrain-translucent-to-entities-1");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift = At.Shift.AFTER, ordinal = 1)
    )
    private void demonica$checkAfterEntitiesPass1(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.check("render-world-pass:" + pass + ":after-entities-1");
        IrisGlDiagnostics.recordWorldPassStage("entities-1-to-render-last");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void demonica$checkAfterRenderLast(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("render-world-pass:" + pass + ":after-render-last");
        IrisGlDiagnostics.recordWorldPassStage("render-last-to-hand");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDiagnostics.check("render-world-pass:" + pass + ":after-hand");
        IrisGlDiagnostics.recordWorldPassStage("hand-to-return");
    }
}
