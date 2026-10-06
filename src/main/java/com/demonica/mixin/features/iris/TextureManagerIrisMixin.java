package com.demonica.mixin.features.iris;

import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public class TextureManagerIrisMixin {
    // Demonica: upstream injects at the TAIL of the reload lambdas, since its texture reload is asynchronous; 1.12.2's
    // is the synchronous onResourceManagerReload. Upstream also calls TextureFormatLoader.reload and
    // PBRTextureManager.clear there; only the counter is ported here (plan item 1.5), and those two calls, which
    // change PBR behaviour on resource reload, are left to their own change. Upstream's dumpAllSheets and close
    // injects have no 1.12.2 target.
    @Inject(method = "onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("TAIL"))
    private void demonica$onTailReload(IResourceManager resourceManager, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.incrementTextureReloadCount();
    }
}
