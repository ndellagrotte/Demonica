package com.demonica.diagnostics.probe;

import com.demonica.diagnostics.dev.DevHarness;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Locale;

import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * Plan item 4.1's probe: a shadow render callback, registered through the public {@code IrisApi}, that draws one
 * horizontal stone quad into the shadow map. The quad is never drawn in the main pass, so all a frame shows of it is
 * the shadow it casts. Dev harness step:
 * <ul>
 *   <li>{@code shadowquad on <dx> <dz> <height> <half>}: draw a quad of half-size {@code half} blocks, {@code height}
 *   blocks above the ground at the player's position plus {@code (dx, dz)}; the first {@code on} registers the
 *   callback</li>
 *   <li>{@code shadowquad off}: keep the callback registered but draw nothing (the API cannot unregister)</li>
 * </ul>
 * The first call after each {@code on} logs the GL state the callback runs in. The bound draw framebuffer is the shadow
 * program pass's own (shadowtex0 depth plus shadowcolor0/1), not the depth-source framebuffer the log also names.
 * Script: {@code run/client/scripts/41shadowquad.txt} (not committed; run/ is local).
 */
public final class ShadowCallbackProbe {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaShadowCallbackProbe");

    private static boolean registered;
    private static volatile boolean enabled;
    private static boolean logNextCall;
    private static double centerX, centerY, centerZ, half;
    private static long calls;

    private ShadowCallbackProbe() {
    }

    public static void install() {
        DevHarness.registerStep("shadowquad", (harness, args) -> step(args));
    }

    private static boolean step(String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (args[1].equalsIgnoreCase("off")) {
            enabled = false;
            LOGGER.info("Shadow quad probe: off after {} calls", calls);
            return true;
        }
        if (mc.player == null || mc.world == null) {
            return false;
        }
        double dx = Double.parseDouble(args[2]);
        double dz = Double.parseDouble(args[3]);
        double height = Double.parseDouble(args[4]);
        half = Double.parseDouble(args[5]);
        centerX = mc.player.posX + dx;
        centerZ = mc.player.posZ + dz;
        int ground = mc.world.getHeight(new BlockPos(centerX, 0, centerZ)).getY();
        centerY = ground + height;
        if (!registered) {
            IrisApi.getInstance().registerShadowRenderCallback(ShadowCallbackProbe::renderShadow);
            registered = true;
        }
        calls = 0;
        logNextCall = true;
        enabled = true;
        LOGGER.info(String.format(Locale.ROOT,
            "Shadow quad probe: on, quad centre (%.2f, %.2f, %.2f), ground y %d, half-size %.1f; API revision %d",
            centerX, centerY, centerZ, ground, half, IrisApi.getInstance().getMinorApiRevision()));
        return true;
    }

    private static void renderShadow(Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY,
                                     double cameraZ, float tickDelta) {
        if (!enabled) {
            return;
        }
        calls++;
        boolean log = logNextCall;
        logNextCall = false;
        String before = log ? glState() : null;

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite stone = mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/stone");
        double x0 = centerX - half - cameraX, x1 = centerX + half - cameraX;
        double z0 = centerZ - half - cameraZ, z1 = centerZ + half - cameraZ;
        double y = centerY - cameraY;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x0, y, z0).tex(stone.getMinU(), stone.getMinV()).color(255, 255, 255, 255).endVertex();
        buffer.pos(x0, y, z1).tex(stone.getMinU(), stone.getMaxV()).color(255, 255, 255, 255).endVertex();
        buffer.pos(x1, y, z1).tex(stone.getMaxU(), stone.getMaxV()).color(255, 255, 255, 255).endVertex();
        buffer.pos(x1, y, z0).tex(stone.getMaxU(), stone.getMinV()).color(255, 255, 255, 255).endVertex();
        tessellator.draw();

        if (log) {
            int depthSourceFb = ShadowRenderer.CURRENT_TARGETS != null
                ? ShadowRenderer.CURRENT_TARGETS.getDepthSourceFb().getId() : -1;
            int passProgram = Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline p
                ? p.getActivePassProgramId() : -1;
            LOGGER.info(String.format(Locale.ROOT,
                "Shadow quad probe: first call: camera (%.2f, %.2f, %.2f), tickDelta %.3f, shadow depth-source framebuffer %d, "
                    + "pipeline pass program %d; before draw: %s; after draw: %s; GLSM model-view == modelView %b, "
                    + "GLSM projection == projection %b",
                cameraX, cameraY, cameraZ, tickDelta, depthSourceFb, passProgram, before, glState(),
                GLStateManager.getModelViewMatrix().equals(modelView, 1.0e-5f),
                GLStateManager.getProjectionMatrix().equals(projection, 1.0e-5f)));
        }
    }

    // The driver's values through the LWJGL service, as GlStateDiffProbe reads them, not GLSM's tracked ones: the
    // question is what the callback's draw really ran with.
    private static String glState() {
        int[] viewport = new int[4];
        LWJGL.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return String.format(Locale.ROOT, "draw framebuffer %d, program %d, viewport %d %d %d %d, cull %b",
            LWJGL.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), LWJGL.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            viewport[0], viewport[1], viewport[2], viewport[3], LWJGL.glGetBoolean(GL11.GL_CULL_FACE));
    }
}
