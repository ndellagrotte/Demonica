package com.demonica.mixin.mod.movingelevators;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moving Elevators draws a moving cage's blocks at HEAD of {@code RenderGlobal.renderBlockLayer} (its
 * {@code LevelRendererMixin}, into {@code ElevatorGroupRenderer.renderBlocks}), with the lightmap texture unit off:
 * vanilla enables it only inside the private one-argument overload, after that HEAD, so the cage is always drawn
 * fullbright, brighter than the blocks around it, with or without Demonica (docs/compat/moving-elevators.md).
 *
 * <p>This enables the lightmap around that draw. The unit is off at HEAD of {@code renderBlockLayer} and Celeritas's
 * overwrite enables and disables it again for its own draw, so the pair is state-neutral. Under a shader pack the
 * enable reaches the GLSM bridge's pass match, so the cage takes the pack's terrain program (texture and lightmap)
 * instead of the plain textured one. Loaded only when Moving Elevators is present (mixins.demonica.conditions.properties).
 */
// Moving Elevators runs in its own names, which no SRG mapping covers: remap = false.
@Mixin(targets = "com.supermartijn642.movingelevators.elevator.ElevatorGroupRenderer", remap = false)
public abstract class MixinElevatorGroupRenderer {

    @Inject(method = "renderBlocks", at = @At("HEAD"))
    private static void demonica$enableLightmap(CallbackInfo ci) {
        Minecraft.getMinecraft().entityRenderer.enableLightmap();
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"))
    private static void demonica$disableLightmap(CallbackInfo ci) {
        Minecraft.getMinecraft().entityRenderer.disableLightmap();
    }
}
