package com.demonica.diagnostics.iris;

import net.coderbot.iris.debug.PBRDebug;
import net.coderbot.iris.texture.pbr.PBRAtlasTexture;
import net.coderbot.iris.texture.pbr.PBRType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;

/** Connects the {@link PBRDebug} facade to {@link PbrDiagnostics}. */
public final class PbrDebugHooks implements PBRDebug.Hooks {
    public static final PbrDebugHooks INSTANCE = new PbrDebugHooks();

    private PbrDebugHooks() {
    }

    @Override
    public void spriteLoaded(PBRType type, ResourceLocation location, TextureAtlasSprite sprite) {
        PbrDiagnostics.spriteLoaded(type, location, sprite);
    }

    @Override
    public void textureLoaded(PBRType type, ResourceLocation location) {
        PbrDiagnostics.textureLoaded(type, location);
    }

    @Override
    public void spriteMissing(PBRType type, ResourceLocation location, Exception exception) {
        PbrDiagnostics.spriteMissing(type, location, exception);
    }

    @Override
    public void spriteFailed(PBRType type, ResourceLocation location, Throwable exception) {
        PbrDiagnostics.spriteFailed(type, location, exception);
    }

    @Override
    public void atlasUploaded(PBRAtlasTexture atlas, int atlasWidth, int atlasHeight, int mipLevel, int spriteCount,
                              int animatedSpriteCount) {
        PbrDiagnostics.atlasUploaded(atlas, atlasWidth, atlasHeight, mipLevel, spriteCount, animatedSpriteCount);
    }

    @Override
    public void atlasClosed(PBRAtlasTexture atlas) {
        PbrDiagnostics.atlasClosed(atlas);
    }
}
