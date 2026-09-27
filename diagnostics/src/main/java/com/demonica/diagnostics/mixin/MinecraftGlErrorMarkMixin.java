package com.demonica.diagnostics.mixin;

import com.demonica.diagnostics.iris.IrisGlDiagnostics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks each of vanilla's glGetError checks as a stage. Priority 900: it runs before Demonica's
 * MinecraftGlErrorCheckMixin, which cancels the "Pre render" and "Post render" checks by default.
 */
@Mixin(value = Minecraft.class, priority = 900)
public class MinecraftGlErrorMarkMixin {
    @Inject(method = "checkGLError(Ljava/lang/String;)V", at = @At("HEAD"))
    private void demonica$markGlErrorCheck(String message, CallbackInfo ci) {
        IrisGlDiagnostics.markStage("minecraft:check-gl-error:" + message);
    }
}
