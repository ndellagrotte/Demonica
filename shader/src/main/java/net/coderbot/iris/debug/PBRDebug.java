package net.coderbot.iris.debug;

import net.coderbot.iris.texture.pbr.PBRAtlasTexture;
import net.coderbot.iris.texture.pbr.PBRType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;

/**
 * PBR resource loading and atlas lifecycle events. Every method is a no-op until the diagnostics jar installs its
 * implementation ({@link Diagnostics}), com.demonica.diagnostics.iris.PbrDiagnostics, which logs them and exports each
 * uploaded atlas while the PBR debug option is on.
 */
public final class PBRDebug {
    /** The implementation's side. */
    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default void spriteLoaded(PBRType type, ResourceLocation location, TextureAtlasSprite sprite) {
        }

        default void textureLoaded(PBRType type, ResourceLocation location) {
        }

        default void spriteMissing(PBRType type, ResourceLocation location, Exception exception) {
        }

        default void spriteFailed(PBRType type, ResourceLocation location, Throwable exception) {
        }

        default void atlasUploaded(PBRAtlasTexture atlas, int atlasWidth, int atlasHeight, int mipLevel, int spriteCount,
                                   int animatedSpriteCount) {
        }

        default void atlasClosed(PBRAtlasTexture atlas) {
        }
    }

    private static Hooks hooks = Hooks.NOOP;

    private PBRDebug() {
    }

    /** Installs the implementation. Called once, on the render thread, by the diagnostics jar. */
    public static void install(Hooks implementation) {
        hooks = implementation != null ? implementation : Hooks.NOOP;
    }

    public static void spriteLoaded(PBRType type, ResourceLocation location, TextureAtlasSprite sprite) {
        hooks.spriteLoaded(type, location, sprite);
    }

    public static void textureLoaded(PBRType type, ResourceLocation location) {
        hooks.textureLoaded(type, location);
    }

    public static void spriteMissing(PBRType type, ResourceLocation location, Exception exception) {
        hooks.spriteMissing(type, location, exception);
    }

    public static void spriteFailed(PBRType type, ResourceLocation location, Throwable exception) {
        hooks.spriteFailed(type, location, exception);
    }

    public static void atlasUploaded(PBRAtlasTexture atlas, int atlasWidth, int atlasHeight, int mipLevel,
                                     int spriteCount, int animatedSpriteCount) {
        hooks.atlasUploaded(atlas, atlasWidth, atlasHeight, mipLevel, spriteCount, animatedSpriteCount);
    }

    public static void atlasClosed(PBRAtlasTexture atlas) {
        hooks.atlasClosed(atlas);
    }
}
