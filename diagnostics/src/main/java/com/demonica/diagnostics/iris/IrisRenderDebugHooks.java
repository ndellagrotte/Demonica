package com.demonica.diagnostics.iris;

import com.demonica.celeritas.api.debug.RenderDebugHooks;
import com.demonica.diagnostics.probe.GlStateDiffProbe;
import org.jetbrains.annotations.Nullable;

/** The Celeritas seam's debug hooks ({@link RenderDebugHooks}), on {@link IrisGlDiagnostics}. */
public final class IrisRenderDebugHooks implements RenderDebugHooks {
    public static final IrisRenderDebugHooks INSTANCE = new IrisRenderDebugHooks();

    private IrisRenderDebugHooks() {
    }

    @Override
    public boolean shouldCaptureGlState() {
        return IrisGlDiagnostics.shouldCaptureGlState();
    }

    @Override
    public @Nullable Object captureGlState() {
        return GlStateDiffProbe.capture();
    }

    @Override
    public void compareGlState(String label, @Nullable Object before) {
        if (before instanceof GlStateDiffProbe.Snapshot snapshot) {
            GlStateDiffProbe.diffAndPrint(label, snapshot, GlStateDiffProbe.capture());
        }
    }

    @Override
    public void checkDrawError(String stage, String source, int drawMode, int vertexFlags, int stride, int vertexCount, String format, int vao, int vbo) {
        IrisGlDiagnostics.checkDrawError(stage, source, drawMode, vertexFlags, stride, vertexCount, format, vao, vbo);
    }

    @Override
    public long beginRenderGlobalStageTiming() {
        return IrisGlDiagnostics.beginRenderGlobalStageTiming();
    }

    @Override
    public void recordRenderGlobalStageTiming(String stage, int pass, long startNanos) {
        IrisGlDiagnostics.recordRenderGlobalStageTiming(stage, pass, startNanos);
    }

    @Override
    public void recordRenderGlobalCounterTiming(String stage, int pass, long nanos, int checked, int visible, int rendered, int outlined, int multipass) {
        IrisGlDiagnostics.recordRenderGlobalCounterTiming(stage, pass, nanos, checked, visible, rendered, outlined, multipass);
    }

    @Override
    public void logWorldPassState(String stage, String phase, String subject) {
        IrisGlDiagnostics.logWorldPassState(stage, phase, subject);
    }

    @Override
    public void logShadowTerrainLayer(String stage, String passName, int visibleChunks) {
        IrisGlDiagnostics.logShadowTerrainLayer(stage, passName, visibleChunks);
    }

    @Override
    public void logCurrentFramebufferSamples(String label, int localColorAttachments) {
        IrisGlDiagnostics.logCurrentFramebufferSamples(label, localColorAttachments);
    }

    @Override
    public void check(String stage) {
        IrisGlDiagnostics.check(stage);
    }

    @Override
    public void logShadowPassState(String stage, boolean terrain, boolean translucent, boolean entities, boolean player, boolean blockEntities, int visibleChunks, int renderedEntities, int renderedBlockEntities) {
        IrisGlDiagnostics.logShadowPassState(stage, terrain, translucent, entities, player, blockEntities, visibleChunks, renderedEntities, renderedBlockEntities);
    }

    @Override
    public void logTerrainMaterialSample(String source, int blockId, int renderType, int lightValue, int localX, int localY, int localZ, float u, float v) {
        IrisGlDiagnostics.logTerrainMaterialSample(source, blockId, renderType, lightValue, localX, localY, localZ, u, v);
    }

    @Override
    public boolean shouldCaptureGpuPerfTiming() {
        return IrisGlDiagnostics.shouldCaptureGpuPerfTiming();
    }

    @Override
    public void recordTerrainRendererTiming(String passName, long totalNanos, long fillNanos, long tessellationNanos, long uniformsNanos, long drawNanos, int regions, int batches, int commands) {
        IrisGlDiagnostics.recordTerrainRendererTiming(passName, totalNanos, fillNanos, tessellationNanos, uniformsNanos, drawNanos, regions, batches, commands);
    }
}
