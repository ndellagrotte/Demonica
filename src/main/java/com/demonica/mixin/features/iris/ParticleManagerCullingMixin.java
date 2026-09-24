package com.demonica.mixin.features.iris;

import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips particles whose bounding box falls outside the last rendered viewport.
 *
 * <p>ParticleCulling declares its own plain {@code @Redirect} on exactly the same two
 * {@code Particle.renderParticle} call sites used here (one in {@code renderParticles},
 * one in {@code renderLitParticles}). Plain redirects cannot be chained: the injector that
 * runs second finds no INVOKE left to redirect and aborts startup with an
 * {@code InjectionError}. Because ParticleCulling already culls particles, this class is
 * skipped entirely by {@code IrisMixinConfigPlugin} when that mod is detected, so its
 * functionality is not lost while the conflicting redirects never coexist.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerCullingMixin {
    @Unique
    private Viewport demonica$cullingViewport;

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void demonica$setupCullingViewportForParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.demonica$setupCullingViewport();
    }

    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void demonica$setupCullingViewportForLitParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.demonica$setupCullingViewport();
    }

    @Redirect(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void demonica$cullParticle(
        Particle particle,
        BufferBuilder buffer,
        Entity entityIn,
        float partialTicks,
        float rotationX,
        float rotationZ,
        float rotationYZ,
        float rotationXY,
        float rotationXZ
    ) {
        this.demonica$renderParticleIfVisible(particle, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    @Redirect(
        method = "renderLitParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void demonica$cullLitParticle(
        Particle particle,
        BufferBuilder buffer,
        Entity entityIn,
        float partialTicks,
        float rotationX,
        float rotationZ,
        float rotationYZ,
        float rotationXY,
        float rotationXZ
    ) {
        this.demonica$renderParticleIfVisible(particle, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    @Unique
    private void demonica$setupCullingViewport() {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instanceNullable();
        this.demonica$cullingViewport = renderer != null ? renderer.getLastViewport() : null;
    }

    @Unique
    private void demonica$renderParticleIfVisible(
        Particle particle,
        BufferBuilder buffer,
        Entity entityIn,
        float partialTicks,
        float rotationX,
        float rotationZ,
        float rotationYZ,
        float rotationXY,
        float rotationXZ
    ) {
        AxisAlignedBB box = particle.getBoundingBox();
        if (this.demonica$cullingViewport == null
                || box == null
                || box == TileEntity.INFINITE_EXTENT_AABB
                || this.demonica$cullingViewport.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
            particle.renderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
    }
}
