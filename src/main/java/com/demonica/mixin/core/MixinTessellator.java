package com.demonica.mixin.core;

import com.gtnewhorizon.gtnhlib.client.renderer.ITessellatorInstance;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.demonica.celeritas.api.shader.vertex.BufferBuilderExtension;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.demonica.render.VanillaBufferBuilderRenderer;

@Mixin(Tessellator.class)
public class MixinTessellator implements ITessellatorInstance {
    @Shadow
    @Final
    private BufferBuilder buffer;

    @Unique
    private boolean demonica$gtnhlibCompiling;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void demonica$coreProfileDraw(CallbackInfo ci) {
        if (VanillaBufferBuilderRenderer.shouldUseVanillaPositionDraw(this.buffer)) {
            return;
        }

        this.buffer.finishDrawing();
        if (TessellatorManager.shouldInterceptBufferBuilderDraw()) {
            TessellatorManager.interceptBufferBuilderDraw(this.buffer);
            ci.cancel();
            return;
        }

        VanillaBufferBuilderRenderer.draw(this.buffer, "Tessellator");
        ci.cancel();
    }

    @Override
    public void discard() {
        if (this.buffer instanceof BufferBuilderExtension extension) {
            extension.demonica$discard();
            return;
        }

        this.buffer.reset();
    }

    @Override
    public boolean gtnhlib$isCompiling() {
        return this.demonica$gtnhlibCompiling;
    }

    @Override
    public void gtnhlib$setCompiling(boolean compiling) {
        this.demonica$gtnhlibCompiling = compiling;
    }
}

