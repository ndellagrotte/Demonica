package com.demonica.mixin.core;

import com.demonica.render.EndPortalCompositeRenderer;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.coderbot.iris.debug.GlFlight;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    // Celeritas's own MinecraftMixin runs the CPU render-ahead limiter; Actinium's copy of it is gone. The GL flight
    // recorder's frame markers are in the diagnostics jar (MinecraftFlightRecorderMixin).
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void beginRenderFrame(CallbackInfo ci) {
        EndPortalCompositeRenderer.beginFrame();
    }

    @Inject(
            method = "runGameLoop",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;updateDisplay()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void endStreamingFrame(CallbackInfo ci) {
        GlFlight.beginTessellatorSync();
        TessellatorStreamingDrawer.endFrame();
        GlFlight.endTessellatorSync();
        GlFlight.beginSwap();
    }
}
