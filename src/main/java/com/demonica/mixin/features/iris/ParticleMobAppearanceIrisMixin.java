package com.demonica.mixin.features.iris;

import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.ParticleMobAppearance;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The elder guardian curse renders a whole guardian model from inside the particle loop. Upstream draws it with
 * the entity programs (vanilla's ElderGuardianParticle uses RenderTypes.entityTranslucent, which
 * I pipeline/IrisPipelines.java maps to ENTITIES_TRANSLUCENT); without this the model takes gbuffers_particles.
 */
@Mixin(ParticleMobAppearance.class)
public class ParticleMobAppearanceIrisMixin {
    @Redirect(
        method = "renderParticle",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V")
    )
    private void demonica$renderGuardianAsEntity(
        RenderManager renderManager,
        Entity entity,
        double x,
        double y,
        double z,
        float yaw,
        float partialTicks,
        boolean debugBoundingBox
    ) {
        if (!IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            renderManager.renderEntity(entity, x, y, z, yaw, partialTicks, debugBoundingBox);
            return;
        }

        GbufferPrograms.drawNestedEntity(
            () -> renderManager.renderEntity(entity, x, y, z, yaw, partialTicks, debugBoundingBox)
        );
    }
}
