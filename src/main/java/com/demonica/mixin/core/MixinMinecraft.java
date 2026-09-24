package com.demonica.mixin.core;

import net.coderbot.iris.debug.flight.GlFlightRecording;
import net.coderbot.iris.debug.flight.GlFlightStreamingSource;
import com.demonica.gui.DemonicaWindowModeController;
import com.demonica.render.BufferBuilderStreamingDrawer;
import com.demonica.runtime.DemonicaRuntime;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        DemonicaWindowModeController.synchronize((Minecraft) (Object) this);
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void demonica$toggleFullscreenMode(CallbackInfo ci) {
        DemonicaWindowModeController.toggleFullscreen((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void demonica$useLoadingScreenFramerateLimit(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.world == null && minecraft.currentScreen != null) {
            cir.setReturnValue(DemonicaRuntime.options().performance.loadingScreenFramerateLimit);
        }
    }

    // Celeritas's own MinecraftMixin runs the CPU render-ahead limiter; Actinium's copy of it is gone.
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void beginRenderFrame(CallbackInfo ci) {
        GlFlightRecording.beginFrame();
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
        GlFlightRecording.beginStreamingSync(GlFlightStreamingSource.TESSELLATOR);
        TessellatorStreamingDrawer.endFrame();
        GlFlightRecording.endStreamingSync(GlFlightStreamingSource.TESSELLATOR);
        GlFlightRecording.beginStreamingSync(GlFlightStreamingSource.BUFFER_BUILDER);
        BufferBuilderStreamingDrawer.endFrame();
        GlFlightRecording.endStreamingSync(GlFlightStreamingSource.BUFFER_BUILDER);
        GlFlightRecording.beginSwap();
    }

    @Inject(
            method = "runGameLoop",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;updateDisplay()V",
                    shift = At.Shift.AFTER
            )
    )
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

