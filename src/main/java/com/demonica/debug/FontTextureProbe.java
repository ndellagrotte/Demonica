package com.demonica.debug;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logs the state of the vanilla font texture ({@code textures/font/ascii.png}) at a few points of startup when
 * {@code -Ddemonica.debug.textureDeletes=true} is set: whether the name is a texture object, its target and level-0
 * size, and GLSM's cached view of texture unit 0. Text that draws as solid boxes means that texture is gone, 1x1, or
 * drawn with texturing off; the line says which.
 */
public final class FontTextureProbe {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaFontProbe");
    private static final boolean ENABLED = Boolean.getBoolean("demonica.debug.textureDeletes");
    private static final ResourceLocation FONT = new ResourceLocation("textures/font/ascii.png");
    private static int framesProbed;

    private FontTextureProbe() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Called at the head of every frame; probes the first few only. */
    public static void onFrame() {
        if (!ENABLED || framesProbed >= 3) return;
        framesProbed++;
        probe("frame " + framesProbed);
    }

    public static void probe(String when) {
        if (!ENABLED) return;
        try {
            final Minecraft mc = Minecraft.getMinecraft();
            final ITextureObject font = mc.getTextureManager() == null ? null : mc.getTextureManager().getTexture(FONT);
            if (font == null) {
                LOGGER.info("[{}] font texture not loaded yet; {}", when, GLStateManager.describeTextureForDebug(0));
                return;
            }
            LOGGER.info("[{}] font {}", when, GLStateManager.describeTextureForDebug(font.getGlTextureId()));
        } catch (Throwable t) {
            LOGGER.warn("[{}] font probe failed", when, t);
        }
    }
}
