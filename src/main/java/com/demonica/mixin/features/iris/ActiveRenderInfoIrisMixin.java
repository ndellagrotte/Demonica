package com.demonica.mixin.features.iris;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(ActiveRenderInfo.class)
public class ActiveRenderInfoIrisMixin {
    @Shadow
    private static FloatBuffer MODELVIEW;

    @Shadow
    private static FloatBuffer PROJECTION;

    // updateRenderInfo(Entity, boolean) is an overload Forge added (the vanilla EntityPlayer method, func_74583_a, now
    // delegates to it). It has no SRG name and keeps its MCP name in production too, so it is named as is, remap = false.
    @Inject(
        method = "updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V",
        at = @At("TAIL"),
        remap = false
    )
    private static void demonica$captureRenderMatrices(CallbackInfo ci) {
        RenderingState.INSTANCE.setProjectionMatrix(PROJECTION);
        RenderingState.INSTANCE.setModelViewMatrix(MODELVIEW);
    }
}
