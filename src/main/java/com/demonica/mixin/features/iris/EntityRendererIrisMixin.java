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
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
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
import org.lwjgl.opengl.GL11;
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
    private void setupFog(int startCoords, float partialTicks) {
    }

    // Set when demonica$renderWeather skips the vanilla-position weather draw so the finalize hook draws it instead. The
    // finalize hook reads and clears it every pass, so weather is drawn exactly once per pass and a flag left behind by an
    // exception cannot cause a stray draw in a later pass.
    @Unique
    private boolean demonica$weatherDeferred;

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
    private void demonica$beginWireframe(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (Iris.shouldActivateWireframe() && this.mc.isSingleplayer()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            this.demonica$wireframeActive = true;
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void demonica$endWireframe(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (this.demonica$wireframeActive) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            this.demonica$wireframeActive = false;
        }
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER)
    )
    private void demonica$beginIrisWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        ShaderTerrain.resetLayer();
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
        // Demonica: upstream sets cloudTime here (MixinLevelRenderer renderLevel HEAD) from the level's game time,
        // wrapped at one period of the cloud texture (width * 400 ticks), and 0 when there is no cloud texture.
        // 1.12.2's clouds scroll by RenderGlobal.cloudTickCounter (opened by the access transformer), so that is
        // the clock packs need to line up with vanilla clouds. Fancy clouds map 256 cells to one texture repeat
        // whatever the texture's size, so the period is the constant 256 * 400 ticks, and the texture always
        // exists. Not set in renderClouds: that runs only while clouds are drawn, and packs that draw their own
        // clouds turn vanilla's off (clouds=off), which would freeze the value.
        CapturedRenderingState.INSTANCE.setCloudTime((this.mc.renderGlobal.cloudTickCounter % (256 * 400) + partialTicks) * 0.03F);
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
        // Terrain setup runs even for a skipAllRendering pack. Upstream's skipSetupRender (MixinLevelRenderer_SkipRendering)
        // has no effect while a pack is in use: it conditions update's cullTerrain call, which iris$setShadows
        // (MixinLevelRenderer) already drops whenever a pack is in use, and iris$renderTerrainShadows calls cullTerrain
        // directly after the shadow pass. So upstream still sets up terrain and builds chunks, which the shadow pass
        // needs; here setupTerrain is where Celeritas builds and uploads them. Only the chunk draws are skipped (below).
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
        // Forge's updateRenderInfo(Entity, boolean) overload, which has no SRG name and keeps its MCP name in production
        // (ActiveRenderInfoIrisMixin): named as is, remap = false.
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V",
            shift = At.Shift.AFTER, remap = false)
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

    // Every terrain layer of the pass (solid, cutout-mipped, cutout, translucent): the layer's Iris phase, the
    // consolidated-cutout skip below and a skipAllRendering pack's.
    //
    // The phase is entered here, around the call, rather than only by S8's HEAD/RETURN pair inside renderBlockLayer
    // (RenderGlobalTerrainMixin). Mixin inserts every HEAD callback before the method's original first instruction in
    // priority order, so a lower-priority mixin's callback runs first: Moving Elevators (priority 1000) draws its moving
    // cage at HEAD of this method, and under S8 alone (1100) that draw ran before the phase was set, into whatever the
    // shadow pass left bound (Actinium #42, docs/compat/moving-elevators.md). S8's pair stays as the fallback for
    // callers outside renderWorldPass (GregTech CEu's bloom wrapper calls renderBlockLayer itself) and is a no-op
    // inside this phase (ShaderTerrain.beginLayer returns false).
    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I"
        )
    )
    private int demonica$renderTerrainLayer(
        RenderGlobal renderGlobal,
        BlockRenderLayer blockLayer,
        double partialTicks,
        int pass,
        Entity entity
    ) {
        if (blockLayer == BlockRenderLayer.CUTOUT && demonica$cutoutSharesCutoutMippedPass()) {
            // Nothing to draw and no phase to enter, as when renderBlockLayer was not called at all.
            return 0;
        }

        boolean entered = ShaderTerrain.beginLayer(blockLayer, (float) partialTicks);
        try {
            // Upstream's skipRenderChunks (MixinLevelRenderer_SkipRendering): a skipAllRendering pack draws no chunk
            // layer. Demonica: a condition inside this existing redirect (widened from the cutout call to all four)
            // rather than upstream's separate @WrapWithCondition. The phases and the translucent call's prelude (the
            // hand's solid parts, then the deferred passes), which upstream runs from separate injections outside the
            // skipped draw (MixinLevelRenderer.iris$beginTranslucents), still run here without the draw.
            if (demonica$skipAllRendering()) {
                return 0;
            }
            return renderGlobal.renderBlockLayer(blockLayer, partialTicks, pass, entity);
        } finally {
            ShaderTerrain.endLayer(entered);
        }
    }

    // Upstream's skipRenderEntities (MixinLevelRenderer_SkipRendering): a skipAllRendering pack draws no entity. Both of
    // Forge's entity passes (0 after the cutout layers, 1 after the translucent layer).
    // Demonica: 1.12.2 draws the entities, lightning (World.weatherEffects) and block entities in one
    // RenderGlobal.renderEntities call, so this skips block entities too. Upstream skips only the entity list and leaves
    // block entities drawn ("TODO IMS 24w35a block entities"). No existing site covers this call, so it is the one
    // injection the item adds.
    @WrapWithCondition(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V")
    )
    private boolean demonica$skipEntitiesForPack(RenderGlobal renderGlobal, Entity renderViewEntity, ICamera camera, float partialTicks) {
        return !demonica$skipAllRendering();
    }

    /**
     * Whether the active pack sets {@code skipAllRendering}: upstream's condition, the pipeline's flag. Demonica: false in a
     * nested renderWorldPass, which the Iris pipeline does not draw (no composites or final would follow the skip).
     */
    @Unique
    private static boolean demonica$skipAllRendering() {
        if (RenderWorldRecursionGuard.isNested() || !Iris.enabled) {
            return false;
        }
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline != null && pipeline.skipAllRendering();
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
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false)
    )
    private void demonica$finalizeIrisWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        boolean weatherDeferred = this.demonica$weatherDeferred;
        this.demonica$weatherDeferred = false;
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            demonica$renderLateClouds(partialTicks, pass, pipeline);
            if (weatherDeferred) {
                demonica$renderLateWeather(partialTicks, pipeline);
            }
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

        // RenderGlobal.renderClouds draws relative to the model-view's origin, the view entity's interpolated feet, and
        // takes its height as cloudHeight - y; vanilla passes those feet (renderWorldPass's d0..d2). The eye would draw
        // the clouds an eye height too low, and the camera's render origin would move them with the F5 camera.
        Entity view = this.mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        double feetX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double feetY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double feetZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        pipeline.setPhase(WorldRenderingPhase.CLOUDS);
        try {
            this.renderCloudsCheck(this.mc.renderGlobal, partialTicks, pass, feetX, feetY, feetZ);
        } finally {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
        if (IrisGlDebug.active()) {
            IrisGlDebug.markStage("render-world-pass:" + pass + ":after-clouds");
        }
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

    // 1.12.2 draws rain and snow before the translucent terrain layer, and so before ShaderTerrain.beginLayer runs the
    // pipeline's beginTranslucents (the hand's solid parts, then the deferred passes). Upstream draws weather in the
    // modern vanilla order, after the translucent layer, the deferred passes and the clouds (MixinLevelRenderer,
    // RENDER_WEATHER). Packs rely on that: Complementary draws its volumetric clouds in a deferred pass limited by
    // depthtex0, which weather does not write, so weather drawn before it ends at the cloud silhouette. While a pack is
    // in use the draw therefore moves to the finalize hook (demonica$renderLateWeather), after the late clouds. Without
    // a pack (the vanilla pipeline has no deferred passes), with Iris off and in a nested pass it stays here.
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
        if (IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            this.demonica$weatherDeferred = true;
            return;
        }

        demonica$drawWeatherPhase(partialTicks, pipeline);
    }

    // The moved weather draw, with the state vanilla's renderWorldPass gives it at its own position: depth writes off,
    // culling on and the terrain fog of setupFog(0), on a flat shade model with blending off (which the end of the
    // translucent section already leaves). Afterwards the state the finalize hook found is put back: depth writes on
    // and fog off. renderRainSnow sets up and restores its own blend, alpha, cull and lightmap state.
    @Unique
    private void demonica$renderLateWeather(float partialTicks, WorldRenderingPipeline pipeline) {
        IrisGlDebug.recordWorldPassStage("weather");
        this.mc.profiler.endStartSection("weather");
        GLStateManager.glDepthMask(false);
        GLStateManager.enableCull();
        this.setupFog(0, partialTicks);
        demonica$drawWeatherPhase(partialTicks, pipeline);
        GLStateManager.glDepthMask(true);
        GLStateManager.disableFog();
        IrisGlDebug.recordWorldPassStage("weather-to-finalize");
    }

    @Unique
    private void demonica$drawWeatherPhase(float partialTicks, WorldRenderingPipeline pipeline) {
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
