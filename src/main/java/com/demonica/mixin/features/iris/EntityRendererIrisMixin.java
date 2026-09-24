package com.demonica.mixin.features.iris;

import com.demonica.celeritas.terrain.CeleritasWorldRendererCompat;
import com.demonica.celeritas.terrain.ShaderTerrain;
import com.demonica.render.GuiGlStateBoundary;
import com.demonica.render.RenderWorldRecursionGuard;
import com.demonica.render.RevoScreenEffectsGradient;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.debug.IrisGlDebug;
import net.coderbot.iris.gl.framebuffer.MinecraftFramebufferHelper;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.SkyRenderDistance;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockRenderLayer;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererIrisMixin implements IResourceManagerReloadListener {
    @Unique
    private boolean demonica$wireframeActive;

    @Shadow
    public Minecraft mc;

    @Shadow
    private void renderCloudsCheck(RenderGlobal renderGlobalIn, float partialTicks, int pass, double x, double y, double z) {
    }

    @Shadow
    protected abstract void renderRainSnow(float partialTicks);

    @Shadow
    private void addRainParticles() {
    }

    // A truly nested renderWorldPass would observe the swapped Minecraft.world and flip the active
    // pipeline mid-pass; the guard lets the Iris injections below skip nested passes. BPR-style
    // sequential portal views are served by PipelineManager's per-dimension cache, not this guard.
    @WrapMethod(method = "renderWorldPass(IFJ)V")
    private void demonica$guardRenderWorldPassRecursion(int pass, float partialTicks, long finishTimeNano, Operation<Void> original) {
        RenderWorldRecursionGuard.enter();
        try {
            original.call(pass, partialTicks, finishTimeNano);
        } finally {
            RenderWorldRecursionGuard.exit();
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void demonica$beginWorldPassTiming(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        IrisGlDebug.beginWorldPassTiming(pass);
        if (Iris.shouldActivateWireframe() && this.mc.isSingleplayer()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            this.demonica$wireframeActive = true;
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void demonica$finishWorldPassTiming(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (this.demonica$wireframeActive) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            this.demonica$wireframeActive = false;
        }
        IrisGlDebug.finishWorldPassTiming();
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER)
    )
    private void demonica$beginIrisWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled) {
            return;
        }

        DHCompat.checkFrame();
        Iris.tryLoadShaderpackWhenPossible();
        if (!IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            // Celeritas may still be meshing and drawing for a pack that was just unloaded.
            ShaderTerrain.reloadIfStale();
            return;
        }

        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());

        Program.unbind();

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimensionName());
        BlockRenderingSettings.INSTANCE.reloadRendererIfRequired();
        ShaderTerrain.reloadIfStale();
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        pipeline.beginLevelRendering();
        IrisGlDebug.markStage("mixin:begin-world:done");
        pipeline.renderPreSkyPrepare();
        IrisGlDebug.markStage("mixin:pre-sky-prepare:done");
        IrisGlDebug.recordWorldPassStage("iris-begin-to-sky");
    }

    // The shadow pass runs first in the frame, just before the terrain pass sets up: upstream Celeritas's shadow
    // protocol has the shadow pass run the frame's terrain search along with its own, and setupTerrain reuse it
    // (docs/celeritas/patches/S7.md).
    @WrapOperation(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V")
    )
    private void demonica$renderIrisShadowsBeforeTerrain(RenderGlobal renderGlobal, Entity entity, double partialTicks, ICamera camera,
                                                        int frame, boolean spectator, Operation<Void> original) {
        this.demonica$renderIrisShadows(camera, (float) partialTicks);
        original.call(renderGlobal, entity, partialTicks, camera, frame, spectator);
    }

    @Unique
    private void demonica$renderIrisShadows(ICamera camera, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            return;
        }

        IrisGlDebug.recordWorldPassStage("shadows");
        demonica$prepareRenderManagerForShadowPass(partialTicks);
        CeleritasWorldRendererCompat.beginFrame(camera);
        try {
            IrisGlDebug.markStage("mixin:shadows:entry");
            pipeline.renderShadows((EntityRenderer) (Object) this, Camera.INSTANCE);
            IrisGlDebug.markStage("mixin:shadows:done");
        } finally {
            CeleritasWorldRendererCompat.endFrame();
        }
        IrisGlDebug.recordWorldPassStage("shadows-to-setup-terrain");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V", shift = At.Shift.AFTER)
    )
    private void demonica$captureCameraState(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        EntityLivingBase viewEntity = (EntityLivingBase) this.mc.getRenderViewEntity();
        if (viewEntity == null) {
            return;
        }

        Camera.INSTANCE.update(viewEntity, partialTicks);
        RenderingState.INSTANCE.setCameraPosition(
            Camera.INSTANCE.getPos().x,
            Camera.INSTANCE.getPos().y,
            Camera.INSTANCE.getPos().z
        );
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
        IrisGlDebug.markStage("entity-renderer:after-render-world");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntityOutlineFramebuffer()V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterEntityOutlineFramebuffer(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-entity-outline-fbo");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/ShaderGroup;render(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterShaderGroup(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-shader-group");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterGameOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-game-overlay");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V")
    )
    private void demonica$restoreBeforeGameOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        // renderItemActivation runs after the first GUI setup and may change state before the HUD starts.
        GuiGlStateBoundary.restoreHudBaseline();
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupOverlayRendering()V")
    )
    private void demonica$restoreBeforeGui(float partialTicks, long nanoTime, CallbackInfo ci) {
        MinecraftFramebufferHelper.restoreMainFramebuffer(true);
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;setupOverlayRendering()V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void demonica$restoreAfterFirstSetupOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        GuiGlStateBoundary.restoreHudBaseline();
        RevoScreenEffectsGradient.drawIfPending(this.mc);
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", shift = At.Shift.AFTER, remap = false)
    )
    private void demonica$checkAfterDrawScreen(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-draw-screen");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", remap = false)
    )
    private void demonica$restoreGuiScreenState(float partialTicks, long nanoTime, CallbackInfo ci) {
        GuiGlStateBoundary.restoreHudBaseline();
    }

    private void demonica$prepareRenderManagerForShadowPass(float partialTicks) {
        RenderManager renderManager = this.mc.getRenderManager();
        renderManager.cacheActiveRenderInfo(
            this.mc.world,
            this.mc.fontRenderer,
            this.mc.getRenderViewEntity(),
            this.mc.pointedEntity,
            this.mc.gameSettings,
            partialTicks
        );
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterClear(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("clear-to-camera");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterCameraTransform(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("camera-to-active-render-info");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterActiveRenderInfo(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("active-render-info-to-frustum");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterClippingHelper(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("clipping-helper-to-culling");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ICamera;setPosition(DDD)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterFrustumPosition(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("culling-to-sky-fog");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupFog(IF)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyFog(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-fog-to-projection");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V")
    )
    private void demonica$checkBeforeSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("render-sky-call");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;matrixMode(I)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyProjectionMatrixMode(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-matrix-mode-to-load-identity");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;loadIdentity()V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyProjectionLoadIdentity(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-load-identity-to-fov");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getFOVModifier(FZ)F", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyFov(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-fov-to-perspective");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSkyPerspective(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-perspective-to-modelview");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-sky");
        IrisGlDebug.recordWorldPassStage("render-sky-to-clouds");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;FIDDD)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterClouds(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (Iris.enabled && IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            IrisGlDebug.recordWorldPassStage("cloud-check-to-setup-terrain");
            return;
        }

        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-clouds");
        IrisGlDebug.recordWorldPassStage("clouds-to-setup-terrain");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-setup-terrain");
        IrisGlDebug.recordWorldPassStage("setup-terrain-to-update-chunks");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;updateChunks(J)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterUpdateChunks(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-update-chunks");
        IrisGlDebug.recordWorldPassStage("update-chunks-to-terrain-solid");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void demonica$checkAfterSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-solid");
        IrisGlDebug.recordWorldPassStage("terrain-solid-to-cutout-mipped");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 1)
    )
    private void demonica$checkAfterCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-cutout-mipped");
        IrisGlDebug.recordWorldPassStage("terrain-cutout-mipped-to-cutout");
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            ordinal = 2
        )
    )
    private int demonica$skipDuplicateConsolidatedCutout(
        RenderGlobal renderGlobal,
        BlockRenderLayer blockLayer,
        double partialTicks,
        int pass,
        Entity entity
    ) {
        if (blockLayer == BlockRenderLayer.CUTOUT && demonica$cutoutSharesCutoutMippedPass()) {
            return 0;
        }

        return renderGlobal.renderBlockLayer(blockLayer, partialTicks, pass, entity);
    }

    /** Whether Celeritas draws cutout geometry in the cutout-mipped pass (render pass consolidation). */
    @Unique
    private static boolean demonica$cutoutSharesCutoutMippedPass() {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instanceNullable();
        if (renderer == null || renderer.getRenderSectionManager() == null) {
            return false;
        }
        var config = renderer.getRenderPassConfiguration();
        return config.getMaterialForRenderType(BlockRenderLayer.CUTOUT_MIPPED).pass
            == config.getMaterialForRenderType(BlockRenderLayer.CUTOUT).pass;
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 2)
    )
    private void demonica$checkAfterCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-cutout");
        IrisGlDebug.recordWorldPassStage("terrain-cutout-to-entities-0");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void demonica$checkAfterEntitiesPass0(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.check("render-world-pass:" + pass + ":after-entities-0");
        IrisGlDebug.recordWorldPassStage("entities-0-to-selection-box");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;IF)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterSelectionBox(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-selection-box");
        IrisGlDebug.recordWorldPassStage("selection-box-to-block-damage");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-block-damage");
        IrisGlDebug.recordWorldPassStage("block-damage-to-lit-particles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-lit-particles");
        IrisGlDebug.recordWorldPassStage("lit-particles-to-particles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-particles");
        IrisGlDebug.recordWorldPassStage("particles-to-weather");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-weather");
        IrisGlDebug.recordWorldPassStage("weather-to-terrain-translucent");
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
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-translucent");
        IrisGlDebug.recordWorldPassStage("terrain-translucent-to-entities-1");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift = At.Shift.AFTER, ordinal = 1)
    )
    private void demonica$checkAfterEntitiesPass1(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.check("render-world-pass:" + pass + ":after-entities-1");
        IrisGlDebug.recordWorldPassStage("entities-1-to-render-last");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void demonica$checkAfterRenderLast(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-render-last");
        IrisGlDebug.recordWorldPassStage("render-last-to-hand");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.check("render-world-pass:" + pass + ":after-hand");
        IrisGlDebug.recordWorldPassStage("hand-to-return");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false)
    )
    private void demonica$finalizeIrisWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            demonica$renderLateClouds(partialTicks, pass, pipeline);
        }

        MinecraftFramebufferHelper.restoreMinecraftFramebufferBuffers();
        IrisGlDebug.markStage("mixin:finalize:entry");
        if (pipeline == null) {
            return;
        }

        IrisGlDebug.markStage("mixin:finalize:before-hand-translucent");
        HandRenderer.INSTANCE.renderTranslucent(partialTicks, Camera.INSTANCE, this.mc.renderGlobal, pipeline);
        IrisGlDebug.markStage("mixin:finalize:after-hand-translucent");
        this.mc.profiler.endStartSection("iris_final");
        pipeline.finalizeLevelRendering();
        IrisGlDebug.markStage("mixin:finalize:after-pipeline");
        Program.unbind();
        GLStateManager.glDepthMask(true);
        IrisGlDebug.markStage("mixin:finalize:done");
        IrisGlDebug.recordWorldPassStage("finalize-to-render-last");
    }

    private void demonica$renderLateClouds(float partialTicks, int pass, WorldRenderingPipeline pipeline) {
        if (pipeline.getCloudSetting() == CloudSetting.OFF) {
            return;
        }

        Vector3d entityPos = Camera.INSTANCE.getEntityPos();
        pipeline.setPhase(WorldRenderingPhase.CLOUDS);
        try {
            this.renderCloudsCheck(
                this.mc.renderGlobal,
                partialTicks,
                pass,
                entityPos.x,
                entityPos.y,
                entityPos.z
            );
        } finally {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-clouds");
        IrisGlDebug.recordWorldPassStage("clouds-to-finalize");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V")
    )
    private void demonica$beginSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
        }
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V", shift = At.Shift.AFTER)
    )
    private void demonica$endSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    // CubicChunks redirects the same renderDistanceChunks reads for its vertical view distance.
    // ModifyExpressionValue composes with that @Redirect instead of fighting it: when Demonica
    // applies first, CubicChunks' value is still clamped by effectiveChunks; when CubicChunks
    // applies first, no field read remains to wrap and this handler stays inactive (require = 0).
    @ModifyExpressionValue(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"),
        require = 0
    )
    private int demonica$alwaysRenderSky(int original) {
        return SkyRenderDistance.effectiveChunks(original);
    }

    // See demonica$alwaysRenderSky for the CubicChunks coexistence contract.
    @ModifyExpressionValue(
        method = "setupCameraTransform(FI)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"),
        require = 0
    )
    private int demonica$alwaysUseSkyRenderDistanceForProjection(int original) {
        return SkyRenderDistance.effectiveChunks(original);
    }

    // See demonica$alwaysRenderSky for the CubicChunks coexistence contract.
    @ModifyExpressionValue(
        method = "updateFogColor(F)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"),
        require = 0
    )
    private int demonica$alwaysApplySunsetColors(int original) {
        return SkyRenderDistance.effectiveChunks(original);
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;FIDDD)V")
    )
    private void demonica$renderClouds(EntityRenderer renderer, RenderGlobal renderGlobal, float partialTicks, int pass, double x, double y, double z) {
        if (RenderWorldRecursionGuard.isNested()) {
            this.renderCloudsCheck(renderGlobal, partialTicks, pass, x, y, z);
            return;
        }
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            this.renderCloudsCheck(renderGlobal, partialTicks, pass, x, y, z);
        }
    }

    @Redirect(
        method = "renderCloudsCheck",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/GameSettings;shouldRenderClouds()I")
    )
    private int demonica$forceCloudsBelowMinimumDistance(GameSettings settings) {
        return settings.renderDistanceChunks < SkyRenderDistance.MINIMUM_RENDER_DISTANCE_CHUNKS
            && settings.clouds == 0 ? 2 : settings.clouds;
    }

    @Inject(
        method = "renderCloudsCheck",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupFog(IF)V", shift = At.Shift.AFTER)
    )
    private void demonica$disableFogForClouds(CallbackInfo ci) {
        if (this.mc.gameSettings.renderDistanceChunks < SkyRenderDistance.MINIMUM_RENDER_DISTANCE_CHUNKS) {
            GLStateManager.disableFog();
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V")
    )
    private void demonica$renderWeather(EntityRenderer renderer, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            this.renderRainSnow(partialTicks);
            return;
        }
        if (!Iris.enabled) {
            this.renderRainSnow(partialTicks);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            this.renderRainSnow(partialTicks);
            return;
        }

        pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
        if (pipeline.shouldWriteRainAndSnowToDepthBuffer()) {
            GLStateManager.glDepthMask(true);
        }
        if (pipeline.shouldRenderWeather()) {
            this.renderRainSnow(partialTicks);
        }
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderWorldBorder(Lnet/minecraft/entity/Entity;F)V")
    )
    private void demonica$renderWorldBorder(RenderGlobal renderGlobal, net.minecraft.entity.Entity entity, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            renderGlobal.renderWorldBorder(entity, partialTicks);
            return;
        }
        if (!Iris.enabled) {
            renderGlobal.renderWorldBorder(entity, partialTicks);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.WORLD_BORDER);
        }
        renderGlobal.renderWorldBorder(entity, partialTicks);
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;renderDebug(FJ)V")
    )
    private void demonica$renderDebug(DebugRenderer debugRenderer, float partialTicks, long finishTimeNano) {
        if (RenderWorldRecursionGuard.isNested()) {
            debugRenderer.renderDebug(partialTicks, finishTimeNano);
            return;
        }
        if (!Iris.enabled) {
            debugRenderer.renderDebug(partialTicks, finishTimeNano);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.DEBUG);
        }
        debugRenderer.renderDebug(partialTicks, finishTimeNano);
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "updateRenderer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;addRainParticles()V")
    )
    private void demonica$renderRainParticles(EntityRenderer renderer) {
        if (!Iris.enabled) {
            this.addRainParticles();
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderWeatherParticles()) {
            this.addRainParticles();
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V")
    )
    private void demonica$renderBlockDamage(RenderGlobal renderGlobal, Tessellator tessellator, net.minecraft.client.renderer.BufferBuilder bufferBuilder, net.minecraft.entity.Entity entity, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
            return;
        }
        if (!Iris.enabled) {
            renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.DESTROY);
        }
        renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "renderHand(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V")
    )
    private void demonica$disableVanillaShaderHand(ItemRenderer itemRenderer, float partialTicks) {
        boolean shaderPackInUse = IrisApi.getInstance().isShaderPackInUse();
        if (!shaderPackInUse) {
            itemRenderer.renderItemInFirstPerson(partialTicks);
        }
    }
}
