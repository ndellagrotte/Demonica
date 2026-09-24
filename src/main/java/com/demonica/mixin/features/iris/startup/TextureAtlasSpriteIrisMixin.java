package com.demonica.mixin.features.iris.startup;

import net.coderbot.iris.texture.pbr.PBRSpriteHolder;
import net.coderbot.iris.texture.pbr.TextureAtlasSpriteExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TextureAtlasSprite.class)
public class TextureAtlasSpriteIrisMixin implements TextureAtlasSpriteExtension {
    @Unique
    private PBRSpriteHolder demonica$pbrHolder;

    @Override
    public PBRSpriteHolder getPBRHolder() {
        return this.demonica$pbrHolder;
    }

    @Override
    public PBRSpriteHolder getOrCreatePBRHolder() {
        if (this.demonica$pbrHolder == null) {
            this.demonica$pbrHolder = new PBRSpriteHolder();
        }

        return this.demonica$pbrHolder;
    }
}
