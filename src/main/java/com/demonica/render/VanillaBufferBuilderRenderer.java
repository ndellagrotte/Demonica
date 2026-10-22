package com.demonica.render;

import com.demonica.render.vertex.QuadSideAttributes;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.debug.GLSMDebug;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.QuadSideAttributeWriter;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import com.demonica.celeritas.api.debug.RenderDebugHooksHolder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public final class VanillaBufferBuilderRenderer {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaBufferBuilder");
    private static final Map<VertexFormat, Integer> VAOS = new HashMap<>();
    /** -Ddemonica.debug.splashDraws=true logs the first BufferBuilder draws issued while the splash screen runs. */
    private static final boolean SPLASH_DRAW_DEBUG = Boolean.getBoolean("demonica.debug.splashDraws");
    private static int splashDrawsLogged;
    private static final Map<VertexFormat, Integer> VBOS = new HashMap<>();
    private static final Map<VertexFormat, Integer> VERTEX_FLAGS = new HashMap<>();
    /**
     * The quad side attributes (mc_midTexCoord and at_tangent, {@link QuadSideAttributeWriter}) of GL_QUADS draws
     * while a shader pack is active: per format, a second vertex array that also points attributes 12 and 13 at a
     * streaming side buffer, and the format's position and texture coordinate offsets (null for a format without
     * them, which keeps the plain vertex array and the constant attributes).
     */
    private static final Map<VertexFormat, Integer> EXTENDED_VAOS = new HashMap<>();
    private static final Map<VertexFormat, Integer> SIDE_VBOS = new HashMap<>();
    private static final Map<VertexFormat, int[]> SIDE_LAYOUTS = new HashMap<>();
    private static ByteBuffer sideScratch;

    private VanillaBufferBuilderRenderer() {
    }

    public static boolean shouldUseVanillaPositionDraw(BufferBuilder bufferBuilder) {
        VertexFormat format = bufferBuilder.getVertexFormat();
        if (format == null || format.getElementCount() != 1) {
            return false;
        }

        VertexFormatElement element = format.getElement(0);
        return element.getUsage() == VertexFormatElement.EnumUsage.POSITION
            && element.getElementCount() == 3
            && element.getType() == VertexFormatElement.EnumType.FLOAT;
    }

    public static void draw(BufferBuilder bufferBuilder, String debugSource) {
        if (bufferBuilder.getVertexCount() <= 0) {
            bufferBuilder.reset();
            return;
        }

        VertexFormat format = bufferBuilder.getVertexFormat();
        int vertexCount = bufferBuilder.getVertexCount();
        int drawMode = bufferBuilder.getDrawMode();
        int stride = format.getSize();
        int byteCount = vertexCount * stride;
        ByteBuffer buffer = bufferBuilder.getByteBuffer().duplicate();
        buffer.position(0);
        buffer.limit(byteCount);

        drawRaw(buffer, format, vertexCount, drawMode, debugSource);
        bufferBuilder.reset();
    }

    public static void drawRaw(ByteBuffer buffer, VertexFormat format, int vertexCount, int drawMode, String debugSource) {
        if (vertexCount <= 0) {
            return;
        }

        ensureDrawState(format);
        int[] sideLayout = drawMode == GL11.GL_QUADS && GLSMHooks.hasActiveConsumers() ? sideLayout(format) : null;
        int vao = sideLayout != null ? ensureExtendedDrawState(format) : VAOS.get(format);
        int vbo = VBOS.get(format);
        int vertexFlags = VERTEX_FLAGS.get(format);
        int stride = format.getSize();
        int byteCount = vertexCount * stride;
        ByteBuffer upload = buffer.duplicate();
        upload.position(0);
        upload.limit(byteCount);

        int savedVbo = GLStateManager.getBoundVBO();
        GLStateManager.glBindVertexArray(vao);
        if (SPLASH_DRAW_DEBUG && !GLStateManager.isSplashComplete() && splashDrawsLogged < 40) {
            splashDrawsLogged++;
            LOGGER.info("[splashDraw] {} mode {} verts {} flags 0x{} vao {} (born on this context: {}) vbo {}; {}; real VAO binding {}",
                format, drawMode, vertexCount, Integer.toHexString(vertexFlags), vao,
                VanillaVertexBufferRenderer.vaoMatchesCurrentContext(vao), vbo,
                GLStateManager.describeSplashStateForDebug(), GLStateManager.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        }
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, upload, GL15.GL_STREAM_DRAW);
        if (sideLayout != null) {
            uploadSideAttributes(format, sideLayout, upload, stride, vertexCount);
        }

        boolean logDrawDiagnostics = GLSMDebug.shouldLogDrawDiagnostics();
        boolean checkDrawErrors = RenderDebugHooksHolder.shouldCaptureGlState();
        String formatDescription = logDrawDiagnostics || checkDrawErrors ? format.toString() : null;
        if (logDrawDiagnostics) {
            GLSMDebug.logBufferBuilderUpload(formatDescription, drawMode, vertexFlags, stride, vertexCount, byteCount, vao, vbo);
        }

        GLStateManager.prepareWideLineEmulation(drawMode);
        ShaderManager.getInstance().preDraw(vertexFlags);
        if (checkDrawErrors) {
            RenderDebugHooksHolder.checkDrawError("bufferbuilder:after-predraw", debugSource, drawMode, vertexFlags, stride, vertexCount, formatDescription, vao, vbo);
        }
        VanillaVertexBufferRenderer.drawArrays(drawMode, vertexCount);
        if (checkDrawErrors) {
            RenderDebugHooksHolder.checkDrawError("bufferbuilder:after-draw", debugSource, drawMode, vertexFlags, stride, vertexCount, formatDescription, vao, vbo);
        }

        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVbo);
        if (checkDrawErrors) {
            RenderDebugHooksHolder.checkDrawError("bufferbuilder:after-restore", debugSource, drawMode, vertexFlags, stride, vertexCount, formatDescription, vao, vbo);
        }
    }

    /**
     * Recreates the per-format VAOs on the current context. Splash replacements can migrate the
     * game to a different GL context when the splash finishes (issue #150); VAOs from the
     * previous context are invalid afterward, while the VBOs are shared and stay valid.
     */
    public static void recreateVertexArrays() {
        for (Map.Entry<VertexFormat, Integer> entry : VAOS.entrySet()) {
            if (!VanillaVertexBufferRenderer.vaoMatchesCurrentContext(entry.getValue())) {
                entry.setValue(VanillaVertexBufferRenderer.createStreamingVertexArray(entry.getKey(), VBOS.get(entry.getKey())));
            }
        }
        for (Map.Entry<VertexFormat, Integer> entry : EXTENDED_VAOS.entrySet()) {
            if (!VanillaVertexBufferRenderer.vaoMatchesCurrentContext(entry.getValue())) {
                entry.setValue(createExtendedVertexArray(entry.getKey(), SIDE_VBOS.get(entry.getKey())));
            }
        }
    }

    public static void destroy() {
        for (int vao : VAOS.values()) {
            GLStateManager.glDeleteVertexArrays(vao);
        }
        for (int vbo : VBOS.values()) {
            GLStateManager.glDeleteBuffers(vbo);
        }
        for (int vao : EXTENDED_VAOS.values()) {
            GLStateManager.glDeleteVertexArrays(vao);
        }
        for (int vbo : SIDE_VBOS.values()) {
            GLStateManager.glDeleteBuffers(vbo);
        }
        VAOS.clear();
        VBOS.clear();
        VERTEX_FLAGS.clear();
        EXTENDED_VAOS.clear();
        SIDE_VBOS.clear();
    }

    private static void ensureDrawState(VertexFormat format) {
        if (!VAOS.containsKey(format)) {
            int vbo = GLStateManager.glGenBuffers();
            int vertexFlags = VanillaVertexBufferRenderer.vertexFlags(format);
            int vao = VanillaVertexBufferRenderer.createStreamingVertexArray(format, vbo);
            VAOS.put(format, vao);
            VBOS.put(format, vbo);
            VERTEX_FLAGS.put(format, vertexFlags);
        }
    }

    private static int[] sideLayout(VertexFormat format) {
        if (!SIDE_LAYOUTS.containsKey(format)) {
            SIDE_LAYOUTS.put(format, QuadSideAttributes.layout(format));
        }
        return SIDE_LAYOUTS.get(format);
    }

    /** The extended vertex array of {@code format}, created with its side buffer on first use. */
    private static int ensureExtendedDrawState(VertexFormat format) {
        Integer vao = EXTENDED_VAOS.get(format);
        if (vao == null) {
            int sideVbo = GLStateManager.glGenBuffers();
            vao = createExtendedVertexArray(format, sideVbo);
            EXTENDED_VAOS.put(format, vao);
            SIDE_VBOS.put(format, sideVbo);
        }
        return vao;
    }

    private static int createExtendedVertexArray(VertexFormat format, int sideVbo) {
        int vao = VanillaVertexBufferRenderer.createStreamingVertexArray(format, VBOS.get(format));
        int savedVao = GLStateManager.getBoundVAO();
        GLStateManager.glBindVertexArray(vao);
        QuadSideAttributeWriter.attachToBoundVertexArray(sideVbo);
        GLStateManager.glBindVertexArray(savedVao);
        return vao;
    }

    private static void uploadSideAttributes(VertexFormat format, int[] layout, ByteBuffer vertices, int stride, int vertexCount) {
        int byteCount = vertexCount * QuadSideAttributeWriter.STRIDE;
        if (sideScratch == null || sideScratch.capacity() < byteCount) {
            sideScratch = ByteBuffer.allocateDirect(Math.max(byteCount, 4096)).order(ByteOrder.nativeOrder());
        }
        QuadSideAttributes.INSTANCE.write(MemoryUtil.memAddress(vertices), stride, layout[0], layout[1], vertexCount,
            MemoryUtil.memAddress(sideScratch, 0));
        sideScratch.position(0);
        sideScratch.limit(byteCount);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, SIDE_VBOS.get(format));
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, sideScratch, GL15.GL_STREAM_DRAW);
    }
}
