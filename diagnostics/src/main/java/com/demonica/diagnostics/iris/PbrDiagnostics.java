package com.demonica.diagnostics.iris;

import com.demonica.config.DemonicaRuntimeOptions;
import net.coderbot.iris.texture.pbr.PBRAtlasTexture;
import net.coderbot.iris.texture.pbr.PBRType;
import net.coderbot.iris.texture.util.TextureExporter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded diagnostics for PBR resource loading and atlas lifecycle events, behind the
 * {@link net.coderbot.iris.debug.PBRDebug} facade, and an export of each uploaded PBR atlas to
 * {@code pbr_debug/atlas}. The debug option is read for every event so the video settings screen takes
 * effect without requiring a client restart.
 */
public final class PbrDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaPBR");
    private static final int MAX_UNIQUE_EVENTS = 512;
    private static final Set<String> REPORTED_EVENTS = Collections.synchronizedSet(new LinkedHashSet<>());

    private PbrDiagnostics() {
    }

    public static void spriteLoaded(PBRType type, ResourceLocation location, TextureAtlasSprite sprite) {
        if (!enabled()) {
            return;
        }

        AnimationMetadataSection metadata = sprite.animationMetadata;
        String event = "loaded:" + type + ":" + location;
        if (record(event)) {
            LOGGER.info("pbr-sprite-loaded type={} location={} size={}x{} frames={} metadata={}",
                type, location, sprite.getIconWidth(), sprite.getIconHeight(), sprite.getFrameCount(),
                metadata == null ? "none" : metadata.getFrameCount());
        }
    }

    public static void textureLoaded(PBRType type, ResourceLocation location) {
        if (!enabled()) {
            return;
        }

        String event = "loaded-texture:" + type + ":" + location;
        if (record(event)) {
            LOGGER.info("pbr-texture-loaded type={} location={}", type, location);
        }
    }

    public static void spriteMissing(PBRType type, ResourceLocation location, Exception exception) {
        if (!enabled()) {
            return;
        }

        String event = "missing:" + type + ":" + location;
        if (record(event)) {
            LOGGER.debug("pbr-sprite-missing type={} location={} reason={}", type, location,
                exception.getClass().getSimpleName());
        }
    }

    public static void spriteFailed(PBRType type, ResourceLocation location, Throwable exception) {
        if (!enabled()) {
            return;
        }

        String event = "failed:" + type + ":" + location;
        if (record(event)) {
            LOGGER.warn("pbr-sprite-failed type={} location={}", type, location, exception);
        }
    }

    public static void atlasUploaded(PBRAtlasTexture atlas, int atlasWidth, int atlasHeight, int mipLevel,
                                     int spriteCount, int animatedSpriteCount) {
        if (!enabled()) {
            return;
        }

        LOGGER.info("pbr-atlas-uploaded type={} id={} size={}x{} mipLevel={} sprites={} animatedSprites={}",
            atlas.getType(), atlas.getAtlasId(), atlasWidth, atlasHeight, mipLevel, spriteCount, animatedSpriteCount);
        ResourceLocation id = atlas.getAtlasId();
        TextureExporter.exportTextures("pbr_debug/atlas", id.getNamespace() + "_" + id.getPath().replaceAll("/", "_"),
            atlas.getGlTextureId(), mipLevel, atlasWidth, atlasHeight);
    }

    public static void atlasClosed(PBRAtlasTexture atlas) {
        if (!enabled()) {
            return;
        }

        LOGGER.info("pbr-atlas-closed type={} id={}", atlas.getType(), atlas.getAtlasId());
    }

    private static boolean enabled() {
        return DemonicaRuntimeOptions.pbrDebugEnabled();
    }

    private static boolean record(String event) {
        synchronized (REPORTED_EVENTS) {
            if (REPORTED_EVENTS.size() >= MAX_UNIQUE_EVENTS) {
                return false;
            }
            return REPORTED_EVENTS.add(event);
        }
    }
}
