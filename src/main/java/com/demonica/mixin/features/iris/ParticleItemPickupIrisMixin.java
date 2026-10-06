package com.demonica.mixin.features.iris;

import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.ParticleItemPickup;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleItemPickup.class)
public class ParticleItemPickupIrisMixin {
    @Redirect(
        method = "renderParticle",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V")
    )
    private void demonica$renderPickupItemAsEntity(
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

        // Demonica: drawNestedEntity puts the particle phase back afterwards; endEntities() alone left NONE, so the
        // lit particles after this one in the same frame drew with the DEFAULT programs.
        GbufferPrograms.drawNestedEntity(
            () -> renderManager.renderEntity(entity, x, y, z, yaw, partialTicks, debugBoundingBox)
        );
    }
}
