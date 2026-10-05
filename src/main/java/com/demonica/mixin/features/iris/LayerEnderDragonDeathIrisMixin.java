package com.demonica.mixin.features.iris;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.entity.layers.LayerEnderDragonDeath;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the dying dragon's light rays with {@code gbuffers_lightning}, as upstream maps its
 * {@code DRAGON_RAYS} and {@code DRAGON_RAYS_DEPTH} render pipelines to {@code ShaderKey.LIGHTNING}
 * ({@code SHADOW_LIGHTNING} in the shadow pass). In 1.12.2 the rays are this layer of
 * {@code RenderDragon}, not the renderer itself, so the special condition wraps the layer.
 */
@Mixin(LayerEnderDragonDeath.class)
public class LayerEnderDragonDeathIrisMixin {
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/boss/EntityDragon;FFFFFFF)V", at = @At("HEAD"))
    private void demonica$beginDeathRays(
        EntityDragon entity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        float scale,
        CallbackInfo ci
    ) {
        if (entity.deathTicks > 0) {
            GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.LIGHTNING);
        }
    }

    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/boss/EntityDragon;FFFFFFF)V", at = @At("RETURN"))
    private void demonica$endDeathRays(
        EntityDragon entity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        float scale,
        CallbackInfo ci
    ) {
        if (entity.deathTicks > 0) {
            GbufferPrograms.teardownSpecialRenderCondition();
        }
    }
}
