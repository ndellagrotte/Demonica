package com.demonica.diagnostics.mixin;

import com.demonica.diagnostics.iris.IrisGlDiagnostics;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The main framebuffer's output to the screen, logged at each step (IrisGlDiagnostics). Priority 1100: at each shared
 * point it runs after Demonica's FramebufferIrisMixin, so the logs see the GL state that mixin prepared.
 */
@Mixin(value = Framebuffer.class, priority = 1100)
public class FramebufferDiagnosticsMixin {
    @Shadow
    public int framebufferTextureWidth;

    @Shadow
    public int framebufferTextureHeight;

    @Shadow
    public int framebufferWidth;

    @Shadow
    public int framebufferHeight;

    @Shadow
    public int framebufferTexture;

    @Unique
    private void demonica$logOutputState(String label, int width, int height, boolean disableBlend) {
        IrisGlDiagnostics.logFramebufferOutputState(
            label,
            this.framebufferTexture,
            this.framebufferWidth,
            this.framebufferHeight,
            this.framebufferTextureWidth,
            this.framebufferTextureHeight,
            width,
            height,
            disableBlend
        );
    }

    @Inject(method = "framebufferRenderExt(IIZ)V", at = @At("HEAD"))
    private void demonica$beginFramebufferOutputDiagnostics(int width, int height, boolean disableBlend, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("framebuffer-output:entry");
        IrisGlDiagnostics.beginFramebufferSamplePhase("minecraft-output");
        this.demonica$logOutputState("entry", width, height, disableBlend);
        IrisGlDiagnostics.logCurrentFramebufferSamples("before-framebuffer-render-ext", 1);
    }

    @Inject(
        method = "framebufferRenderExt(IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;bindFramebufferTexture()V")
    )
    private void demonica$beforeFramebufferTextureBind(int width, int height, boolean disableBlend, CallbackInfo ci) {
        this.demonica$logOutputState("before-bind-texture", width, height, disableBlend);
    }

    @Inject(
        method = "framebufferRenderExt(IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;bindFramebufferTexture()V", shift = At.Shift.AFTER)
    )
    private void demonica$afterFramebufferTextureBind(int width, int height, boolean disableBlend, CallbackInfo ci) {
        this.demonica$logOutputState("after-bind-texture", width, height, disableBlend);
    }

    @Inject(
        method = "framebufferRenderExt(IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V")
    )
    private void demonica$beforeFramebufferOutputDraw(int width, int height, boolean disableBlend, CallbackInfo ci) {
        this.demonica$logOutputState("before-draw", width, height, disableBlend);
    }

    @Inject(
        method = "framebufferRenderExt(IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", shift = At.Shift.AFTER)
    )
    private void demonica$afterFramebufferOutputDraw(int width, int height, boolean disableBlend, CallbackInfo ci) {
        this.demonica$logOutputState("after-draw", width, height, disableBlend);
    }

    @Inject(method = "framebufferRenderExt(IIZ)V", at = @At("RETURN"))
    private void demonica$endFramebufferOutputDiagnostics(int width, int height, boolean disableBlend, CallbackInfo ci) {
        this.demonica$logOutputState("return", width, height, disableBlend);
        IrisGlDiagnostics.logCurrentFramebufferSamples("after-framebuffer-render-ext", 1);
        IrisGlDiagnostics.endFramebufferSamplePhase();
        IrisGlDiagnostics.markStage("framebuffer-output:return");
    }
}
