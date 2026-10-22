package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * In-process capture of the loading screen: {@code -Ddemonica.debug.splashDump=N} writes the finished back buffer to
 * {@code screenshots/splash-<n>.png} on every Nth {@code Display.update()} issued off the main thread before the splash
 * is complete (at most {@link #MAX_DUMPS} files). The splash runs in its own thread and GL context, so a screenshot
 * tool cannot be pointed at it reliably; this reads the frame right before it is swapped, on the thread that drew it.
 */
final class SplashFrameDump {
    private static final int EVERY = Integer.getInteger("demonica.debug.splashDump", 0);
    private static final int MAX_DUMPS = 40;
    private static int frames;
    private static int dumps;
    private static final IntBuffer VIEWPORT = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();

    private SplashFrameDump() {
    }

    static boolean isEnabled() {
        return EVERY > 0;
    }

    /** Called from {@link GLStateManager#updateDisplay()} before the swap; cheap when the property is unset. */
    static void beforeSwap() {
        if (EVERY <= 0 || dumps >= MAX_DUMPS || GLStateManager.isSplashComplete()) return;
        if (Thread.currentThread() == GLStateManager.getMainThread()) return;
        if (++frames % EVERY != 0) return;
        try {
            final var backend = com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
            VIEWPORT.clear();
            backend.getInteger(GL11.GL_VIEWPORT, VIEWPORT);
            final int width = VIEWPORT.get(2);
            final int height = VIEWPORT.get(3);
            if (width <= 0 || height <= 0) return;
            final ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            backend.readBuffer(GL11.GL_BACK);
            backend.readPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int i = ((height - 1 - y) * width + x) * 4;
                    final int r = pixels.get(i) & 0xFF, g = pixels.get(i + 1) & 0xFF, b = pixels.get(i + 2) & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            final File dir = new File("screenshots");
            dir.mkdirs();
            final File out = new File(dir, "splash-" + (++dumps) + ".png");
            ImageIO.write(image, "png", out);
            GLStateManager.LOGGER.info("[splashDump] wrote {} ({}x{}) after {} frames on {}; {}", out, width, height, frames,
                Thread.currentThread().getName(), GLStateManager.describeSplashStateForDebug());
        } catch (Throwable t) {
            GLStateManager.LOGGER.warn("[splashDump] failed", t);
        }
    }
}
