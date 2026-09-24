package com.demonica.mixin.features.iris.startup;

import net.coderbot.iris.texture.pbr.PBRAtlasHolder;
import net.coderbot.iris.texture.pbr.TextureAtlasExtension;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class TextureMapIrisMixin extends AbstractTexture implements TextureAtlasExtension {
    @Unique
    private PBRAtlasHolder demonica$pbrHolder;

    @Inject(method = "updateAnimations()V", at = @At("TAIL"))
    private void demonica$cyclePbrAnimationFrames(CallbackInfo ci) {
        if (this.demonica$pbrHolder != null) {
            this.demonica$pbrHolder.cycleAnimationFrames();
        }
    }

    @Override
    public PBRAtlasHolder getPBRHolder() {
        return this.demonica$pbrHolder;
    }

    @Override
    public PBRAtlasHolder getOrCreatePBRHolder() {
        if (this.demonica$pbrHolder == null) {
            this.demonica$pbrHolder = new PBRAtlasHolder();
        }

        return this.demonica$pbrHolder;
    }
}
