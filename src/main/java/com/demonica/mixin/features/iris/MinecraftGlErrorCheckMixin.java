package com.demonica.mixin.features.iris;

import com.demonica.config.DemonicaRuntimeOptions;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips vanilla's per-frame "Pre render" and "Post render" glGetError checks, each a pipeline stall, unless the Debug
 * page's GL error checks (or -Ddemonica.frameGlErrorCheck, -Ddemonica.postRenderGlErrorCheck) turn them on.
 */
@Mixin(Minecraft.class)
public class MinecraftGlErrorCheckMixin {
    @Inject(method = "checkGLError(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void demonica$skipFrameGlErrorChecks(String message, CallbackInfo ci) {
        if ("Pre render".equals(message) && !DemonicaRuntimeOptions.checkPreRenderGlErrors()) {
            ci.cancel();
        } else if ("Post render".equals(message) && !DemonicaRuntimeOptions.checkPostRenderGlErrors()) {
            ci.cancel();
        }
    }
}
