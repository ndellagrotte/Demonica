package com.gtnewhorizons.angelica.glsm.debug;

import java.nio.ByteBuffer;

/**
 * GLSM's draw logs, as called from GLSM's draw paths and the GL redirector. Every method is a no-op until the
 * diagnostics jar installs its implementation (com.demonica.diagnostics.glsm.GlsmDrawLog), which its coremod does
 * before most classes are transformed, so the redirector's reports cover them.
 */
public final class GLSMDebug {
    /** The implementation's side. */
    public interface Sink {
        Sink NOOP = new Sink() {
        };

        default boolean isEnabled() {
            return false;
        }

        default boolean shouldLogDrawDiagnostics() {
            return false;
        }

        default void logStreamingDraw(int drawMode, int flags, int vertexSize, int vertexCount, int byteCount,
            int firstVertex, boolean persistent, int vao, int bufferId, ByteBuffer packed) {
        }

        default void logQuadConversion(int firstVertex, int vertexCount, int eboId, int prevEbo, long indexOffset) {
        }

        default void logFfpPreDraw(int flags) {
        }

        default void logDrawArrays(String path, int mode, int first, int count) {
        }

        default void logDrawElements(String path, int mode, int count, int type, long offset) {
        }

        default void logClientArrayUpload(int totalBytes, int capacity, int targetVbo, int savedVbo, int[] offsets) {
        }

        default void logBufferBuilderUpload(String format, int drawMode, int vertexFlags, int stride, int vertexCount,
            int byteCount, int vao, int vbo) {
        }

        default void logVertexBufferUpload(String format, int vertexFlags, int stride, int byteCount, int vbo, int vao) {
        }

        default void logVertexBufferDraw(String format, int drawMode, int vertexFlags, int stride, int vertexCount,
            int vbo, int vao) {
        }

        default void logDrawOnActiveProgram(String path, int drawMode, int flags, int vertexCount, int program) {
        }
    }

    private static Sink sink = Sink.NOOP;

    private GLSMDebug() {
    }

    /** Installs the implementation. Called once by the diagnostics jar's coremod. */
    public static void install(Sink implementation) {
        sink = implementation != null ? implementation : Sink.NOOP;
    }

    public static boolean isEnabled() {
        return sink.isEnabled();
    }

    public static boolean shouldLogDrawDiagnostics() {
        return sink.shouldLogDrawDiagnostics();
    }

    public static void logStreamingDraw(int drawMode, int flags, int vertexSize, int vertexCount, int byteCount,
        int firstVertex, boolean persistent, int vao, int bufferId, ByteBuffer packed) {
        sink.logStreamingDraw(drawMode, flags, vertexSize, vertexCount, byteCount, firstVertex, persistent, vao, bufferId,
            packed);
    }

    public static void logQuadConversion(int firstVertex, int vertexCount, int eboId, int prevEbo, long indexOffset) {
        sink.logQuadConversion(firstVertex, vertexCount, eboId, prevEbo, indexOffset);
    }

    public static void logFfpPreDraw(int flags) {
        sink.logFfpPreDraw(flags);
    }

    public static void logDrawArrays(String path, int mode, int first, int count) {
        sink.logDrawArrays(path, mode, first, count);
    }

    public static void logDrawElements(String path, int mode, int count, int type, long offset) {
        sink.logDrawElements(path, mode, count, type, offset);
    }

    public static void logClientArrayUpload(int totalBytes, int capacity, int targetVbo, int savedVbo, int[] offsets) {
        sink.logClientArrayUpload(totalBytes, capacity, targetVbo, savedVbo, offsets);
    }

    public static void logBufferBuilderUpload(String format, int drawMode, int vertexFlags, int stride, int vertexCount,
        int byteCount, int vao, int vbo) {
        sink.logBufferBuilderUpload(format, drawMode, vertexFlags, stride, vertexCount, byteCount, vao, vbo);
    }

    public static void logVertexBufferUpload(String format, int vertexFlags, int stride, int byteCount, int vbo, int vao) {
        sink.logVertexBufferUpload(format, vertexFlags, stride, byteCount, vbo, vao);
    }

    public static void logVertexBufferDraw(String format, int drawMode, int vertexFlags, int stride, int vertexCount,
        int vbo, int vao) {
        sink.logVertexBufferDraw(format, drawMode, vertexFlags, stride, vertexCount, vbo, vao);
    }

    public static void logDrawOnActiveProgram(String path, int drawMode, int flags, int vertexCount, int program) {
        sink.logDrawOnActiveProgram(path, drawMode, flags, vertexCount, program);
    }
}
