package com.demonica.diagnostics.glsm;

import com.gtnewhorizons.angelica.glsm.debug.GLSMDebug;

import java.nio.ByteBuffer;

/** Connects the {@link GLSMDebug} facade to {@link GlsmDrawLog}. */
public final class GlsmDrawLogSink implements GLSMDebug.Sink {
    public static final GlsmDrawLogSink INSTANCE = new GlsmDrawLogSink();

    private GlsmDrawLogSink() {
    }

    @Override
    public boolean isEnabled() {
        return GlsmDrawLog.isEnabled();
    }

    @Override
    public boolean shouldLogDrawDiagnostics() {
        return GlsmDrawLog.shouldLogDrawDiagnostics();
    }

    @Override
    public void logStreamingDraw(int drawMode, int flags, int vertexSize, int vertexCount, int byteCount,
        int firstVertex, boolean persistent, int vao, int bufferId, ByteBuffer packed) {
        GlsmDrawLog.logStreamingDraw(drawMode, flags, vertexSize, vertexCount, byteCount, firstVertex, persistent, vao,
            bufferId, packed);
    }

    @Override
    public void logQuadConversion(int firstVertex, int vertexCount, int eboId, int prevEbo, long indexOffset) {
        GlsmDrawLog.logQuadConversion(firstVertex, vertexCount, eboId, prevEbo, indexOffset);
    }

    @Override
    public void logFfpPreDraw(int flags) {
        GlsmDrawLog.logFfpPreDraw(flags);
    }

    @Override
    public void logDrawArrays(String path, int mode, int first, int count) {
        GlsmDrawLog.logDrawArrays(path, mode, first, count);
    }

    @Override
    public void logDrawElements(String path, int mode, int count, int type, long offset) {
        GlsmDrawLog.logDrawElements(path, mode, count, type, offset);
    }

    @Override
    public void logClientArrayUpload(int totalBytes, int capacity, int targetVbo, int savedVbo, int[] offsets) {
        GlsmDrawLog.logClientArrayUpload(totalBytes, capacity, targetVbo, savedVbo, offsets);
    }

    @Override
    public void logBufferBuilderUpload(String format, int drawMode, int vertexFlags, int stride, int vertexCount,
        int byteCount, int vao, int vbo) {
        GlsmDrawLog.logBufferBuilderUpload(format, drawMode, vertexFlags, stride, vertexCount, byteCount, vao, vbo);
    }

    @Override
    public void logVertexBufferUpload(String format, int vertexFlags, int stride, int byteCount, int vbo, int vao) {
        GlsmDrawLog.logVertexBufferUpload(format, vertexFlags, stride, byteCount, vbo, vao);
    }

    @Override
    public void logVertexBufferDraw(String format, int drawMode, int vertexFlags, int stride, int vertexCount,
        int vbo, int vao) {
        GlsmDrawLog.logVertexBufferDraw(format, drawMode, vertexFlags, stride, vertexCount, vbo, vao);
    }

    @Override
    public void logDrawOnActiveProgram(String path, int drawMode, int flags, int vertexCount, int program) {
        GlsmDrawLog.logDrawOnActiveProgram(path, drawMode, flags, vertexCount, program);
    }
}
