package com.demonica.mixin.features.iris;

import net.coderbot.iris.debug.IrisGlDebug;
import net.minecraft.client.Minecraft;
import com.demonica.gl.TimerQueryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftGlDebugMixin {
    @Unique
    private TimerQueryManager demonica$frameOutputTimer;
    @Unique
    private TimerQueryManager demonica$frameRenderTimer;
    @Unique
    private long demonica$frameOutputCpuStart;
    @Unique
    private long demonica$frameRenderCpuStart;
    @Unique
    private boolean demonica$frameOutputProfiling;
    @Unique
    private boolean demonica$frameRenderProfiling;
    @Unique
    private long demonica$gameLoopStart;
    @Unique
    private long demonica$runTickStart;
    @Unique
    private long demonica$gameRendererStart;
    @Unique
    private long demonica$frameOutputStart;
    @Unique
    private long demonica$streamIndicatorStart;
    @Unique
    private long demonica$updateDisplayStart;
    @Unique
    private long demonica$scheduledTasksStart;
    @Unique
    private long demonica$preRenderGlErrorStart;
    @Unique
    private long demonica$soundListenerStart;
    @Unique
    private long demonica$renderSetupStart;
    @Unique
    private long demonica$fmlRenderTickStartStart;
    @Unique
    private long demonica$toastStart;
    @Unique
    private long demonica$fmlRenderTickEndStart;
    @Unique
    private long demonica$debugInfoStart;
    @Unique
    private long demonica$postRenderGlErrorStart;
    @Unique
    private long demonica$pauseStateStart;
    @Unique
    private long demonica$frameTimerStart;

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void demonica$startGameLoopTiming(CallbackInfo ci) {
        this.demonica$gameLoopStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void demonica$finishGameLoopTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("total", this.demonica$gameLoopStart);
        IrisGlDebug.incrementGameLoopFrameCount();
    }

    @Inject(method = "runTick()V", at = @At("HEAD"))
    private void demonica$startRunTickTiming(CallbackInfo ci) {
        this.demonica$runTickStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(method = "runTick()V", at = @At("RETURN"))
    private void demonica$finishRunTickTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("run-tick", this.demonica$runTickStart);
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z")
    )
    private void demonica$startScheduledTasksTiming(CallbackInfo ci) {
        this.demonica$scheduledTasksStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;checkGLError(Ljava/lang/String;)V", ordinal = 0)
    )
    private void demonica$finishScheduledTasksAndStartPreRenderGlErrorTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("scheduled-tasks", this.demonica$scheduledTasksStart);
        this.demonica$preRenderGlErrorStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundHandler;setListener(Lnet/minecraft/entity/Entity;F)V")
    )
    private void demonica$finishPreRenderGlErrorAndStartSoundTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("pre-render-gl-error", this.demonica$preRenderGlErrorStart);
        this.demonica$soundListenerStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V", ordinal = 0)
    )
    private void demonica$finishSoundAndStartRenderSetupTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("sound-listener", this.demonica$soundListenerStart);
        this.demonica$renderSetupStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onRenderTickStart(F)V", remap = false)
    )
    private void demonica$finishRenderSetupAndStartFmlRenderTickStartTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("render-setup", this.demonica$renderSetupStart);
        this.demonica$fmlRenderTickStartStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(method = "checkGLError(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void demonica$markGlErrorCheck(String message, CallbackInfo ci) {
        IrisGlDebug.markStage("minecraft:check-gl-error:" + message);
        if ("Pre render".equals(message) && !IrisGlDebug.shouldCheckPreRenderGlErrors()) {
            ci.cancel();
        } else if ("Post render".equals(message) && !IrisGlDebug.shouldCheckPostRenderGlErrors()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(FJ)V")
    )
    private void demonica$startFrameRenderTimer(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("fml-render-tick-start", this.demonica$fmlRenderTickStartStart);
        this.demonica$gameRendererStart = IrisGlDebug.beginGameLoopStageTiming();
        if (!IrisGlDebug.shouldCaptureGpuPerfTiming()) {
            this.demonica$frameRenderProfiling = false;
            return;
        }

        if (this.demonica$frameRenderTimer == null) {
            this.demonica$frameRenderTimer = new TimerQueryManager();
        }

        this.demonica$frameRenderTimer.updateTime();
        this.demonica$frameRenderCpuStart = System.nanoTime();
        this.demonica$frameRenderProfiling = true;
        this.demonica$frameRenderTimer.startProfiling();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(FJ)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterGameRenderer(CallbackInfo ci) {
        if (this.demonica$frameRenderProfiling) {
            this.demonica$frameRenderTimer.finishProfiling();
            IrisGlDebug.logFrameRenderTiming(System.nanoTime() - this.demonica$frameRenderCpuStart, this.demonica$frameRenderTimer.getLastTime());
            this.demonica$frameRenderProfiling = false;
        }
        IrisGlDebug.recordGameLoopStageTiming("game-renderer", this.demonica$gameRendererStart);

        IrisGlDebug.logWhiteScreenProbe("after-game-renderer");
        IrisGlDebug.check("minecraft:after-game-renderer");
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/toasts/GuiToast;drawToast(Lnet/minecraft/client/gui/ScaledResolution;)V")
    )
    private void demonica$startToastTiming(CallbackInfo ci) {
        this.demonica$toastStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/toasts/GuiToast;drawToast(Lnet/minecraft/client/gui/ScaledResolution;)V", shift = At.Shift.AFTER)
    )
    private void demonica$finishToastTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("toast", this.demonica$toastStart);
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", remap = false)
    )
    private void demonica$startFmlRenderTickEndTiming(CallbackInfo ci) {
        this.demonica$fmlRenderTickEndStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void demonica$finishFmlRenderTickEndTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("fml-render-tick-end", this.demonica$fmlRenderTickEndStart);
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayDebugInfo(J)V")
    )
    private void demonica$startDebugInfoTiming(CallbackInfo ci) {
        this.demonica$debugInfoStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayDebugInfo(J)V", shift = At.Shift.AFTER)
    )
    private void demonica$finishDebugInfoTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("debug-info", this.demonica$debugInfoStart);
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;bindFramebuffer(Z)V", shift = At.Shift.AFTER)
    )
    private void demonica$probeAfterMainFramebufferBind(CallbackInfo ci) {
        IrisGlDebug.logWhiteScreenProbe("after-main-fbo-bind");
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V")
    )
    private void demonica$startFrameOutputTimer(CallbackInfo ci) {
        this.demonica$frameOutputStart = IrisGlDebug.beginGameLoopStageTiming();
        if (!IrisGlDebug.shouldCaptureGpuPerfTiming()) {
            this.demonica$frameOutputProfiling = false;
            return;
        }

        if (this.demonica$frameOutputTimer == null) {
            this.demonica$frameOutputTimer = new TimerQueryManager();
        }

        this.demonica$frameOutputTimer.updateTime();
        this.demonica$frameOutputCpuStart = System.nanoTime();
        this.demonica$frameOutputProfiling = true;
        this.demonica$frameOutputTimer.startProfiling();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterFramebufferRender(CallbackInfo ci) {
        if (this.demonica$frameOutputProfiling) {
            this.demonica$frameOutputTimer.finishProfiling();
            IrisGlDebug.logFrameOutputTiming(System.nanoTime() - this.demonica$frameOutputCpuStart, this.demonica$frameOutputTimer.getLastTime());
            this.demonica$frameOutputProfiling = false;
        }
        IrisGlDebug.recordGameLoopStageTiming("framebuffer-output", this.demonica$frameOutputStart);

        IrisGlDebug.logWhiteScreenProbe("after-framebuffer-render");
        IrisGlDebug.markStage("minecraft:after-framebuffer-render");
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderStreamIndicator(F)V")
    )
    private void demonica$startStreamIndicatorTiming(CallbackInfo ci) {
        this.demonica$streamIndicatorStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderStreamIndicator(F)V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterStreamIndicator(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("stream-indicator", this.demonica$streamIndicatorStart);
        IrisGlDebug.markStage("minecraft:after-stream-indicator");
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V")
    )
    private void demonica$markBeforeUpdateDisplay(CallbackInfo ci) {
        this.demonica$updateDisplayStart = IrisGlDebug.beginGameLoopStageTiming();
        IrisGlDebug.markStage("minecraft:before-update-display");
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V", shift = At.Shift.AFTER)
    )
    private void demonica$checkAfterUpdateDisplay(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("update-display", this.demonica$updateDisplayStart);
        IrisGlDebug.markStage("minecraft:after-update-display");
        IrisGlDebug.logWhiteScreenProbe("after-update-display");
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V", shift = At.Shift.AFTER)
    )
    private void demonica$startPostRenderGlErrorTiming(CallbackInfo ci) {
        this.demonica$postRenderGlErrorStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;fpsCounter:I", ordinal = 0)
    )
    private void demonica$finishPostRenderGlErrorAndStartPauseStateTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("post-render-gl-error", this.demonica$postRenderGlErrorStart);
        this.demonica$pauseStateStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;frameTimer:Lnet/minecraft/util/FrameTimer;", ordinal = 0)
    )
    private void demonica$finishPauseStateAndStartFrameTimerTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("pause-state", this.demonica$pauseStateStart);
        this.demonica$frameTimerStart = IrisGlDebug.beginGameLoopStageTiming();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;startNanoTime:J", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER)
    )
    private void demonica$finishFrameTimerTiming(CallbackInfo ci) {
        IrisGlDebug.recordGameLoopStageTiming("frame-timer", this.demonica$frameTimerStart);
    }
}
