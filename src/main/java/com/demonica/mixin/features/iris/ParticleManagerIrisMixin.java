package com.demonica.mixin.features.iris;

import com.gtnewhorizons.angelica.client.rendering.DeferredDrawBatcher;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class ParticleManagerIrisMixin {
    @Unique
    private WorldRenderingPhase demonica$previousParticlePhase;

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void demonica$beginParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.demonica$beginParticlePhase();
    }

    @Inject(method = "renderParticles", at = @At("RETURN"))
    private void demonica$endParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        DeferredDrawBatcher.exitAndFlush();
        this.demonica$endParticlePhase();
    }

    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void demonica$beginLitParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.demonica$beginParticlePhase();
    }

    @Inject(method = "renderLitParticles", at = @At("RETURN"))
    private void demonica$endLitParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.demonica$endParticlePhase();
    }

    @Inject(
        method = "renderParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
            shift = At.Shift.AFTER
        )
    )
    private void demonica$enterDeferredParticleBatch(Entity entityIn, float partialTicks, CallbackInfo ci) {
        DeferredDrawBatcher.enter();
    }

    @Inject(
        method = "renderParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"
        )
    )
    private void demonica$exitDeferredParticleBatch(Entity entityIn, float partialTicks, CallbackInfo ci) {
        DeferredDrawBatcher.exitAndFlush();
    }

    /**
     * Matches GTNH Angelica's notfine particle fix: avoid the first depth-mask disable in the vanilla
     * particle loop and leave depth state under the render pipeline's control.
     */
    @Redirect(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;depthMask(Z)V", ordinal = 0)
    )
    private void demonica$skipFirstParticleDepthMask(boolean flag) {
    }

    @Unique
    private void demonica$beginParticlePhase() {
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            this.demonica$previousParticlePhase = null;
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            this.demonica$previousParticlePhase = null;
            return;
        }

        this.demonica$previousParticlePhase = pipeline.getPhase();
        pipeline.setPhase(WorldRenderingPhase.PARTICLES);
    }

    @Unique
    private void demonica$endParticlePhase() {
        if (this.demonica$previousParticlePhase == null) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(this.demonica$previousParticlePhase);
        }

        this.demonica$previousParticlePhase = null;
    }
}

