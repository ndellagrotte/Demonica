package com.demonica.diagnostics.mixin;

import com.demonica.diagnostics.flight.GlFlightRecording;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The GL flight recorder's frames: begun first thing in the game loop, closed after the swap and at the end of the
 * loop, and the recording closed cleanly at shutdown. Priority 900, so that at each point it runs before Demonica's own
 * frame work (MixinMinecraft) and the timing table, as it did inside the mod.
 */
@Mixin(value = Minecraft.class, priority = 900)
public class MinecraftFlightRecorderMixin {
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void demonica$beginFlightRecorderFrame(CallbackInfo ci) {
        GlFlightRecording.beginFrame();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V", shift = At.Shift.AFTER))
    private void demonica$finishDisplaySwap(CallbackInfo ci) {
        GlFlightRecording.endSwap();
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void demonica$finishFlightRecorderFrame(CallbackInfo ci) {
        GlFlightRecording.endFrame();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("RETURN"))
    private void demonica$closeFlightRecorder(CallbackInfo ci) {
        GlFlightRecording.closeNormally();
    }
}
