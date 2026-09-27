package com.demonica.diagnostics.iris;

import net.coderbot.iris.debug.IrisGlDebug;
import net.coderbot.iris.rendertarget.RenderTargets;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute;

import java.util.Collection;

/** Connects the {@link IrisGlDebug} facade to {@link IrisGlDiagnostics}. */
public final class IrisGlDebugHooks implements IrisGlDebug.Hooks {
    public static final IrisGlDebugHooks INSTANCE = new IrisGlDebugHooks();

    private IrisGlDebugHooks() {
    }

    @Override
    public void beginFramebufferSamplePhase(String phase) {
        IrisGlDiagnostics.beginFramebufferSamplePhase(phase);
    }

    @Override
    public long beginGameLoopStageTiming() {
        return IrisGlDiagnostics.beginGameLoopStageTiming();
    }

    @Override
    public void beginWorldPassTiming(int pass) {
        IrisGlDiagnostics.beginWorldPassTiming(pass);
    }

    @Override
    public void check(String stage) {
        IrisGlDiagnostics.check(stage);
    }

    @Override
    public void endFramebufferSamplePhase() {
        IrisGlDiagnostics.endFramebufferSamplePhase();
    }

    @Override
    public void finishWorldPassTiming() {
        IrisGlDiagnostics.finishWorldPassTiming();
    }

    @Override
    public void incrementGameLoopFrameCount() {
        IrisGlDiagnostics.incrementGameLoopFrameCount();
    }

    @Override
    public boolean isCloudControlDebugEnabled() {
        return IrisGlDiagnostics.isCloudControlDebugEnabled();
    }

    @Override
    public void logCeleritasProgram(String passName, int program, Collection<GlVertexAttribute> attributes) {
        IrisGlDiagnostics.logCeleritasProgram(passName, program, attributes);
    }

    @Override
    public void logCeleritasTerrainState(String passName, int program, boolean hasAlphaTestOverride,
        float expectedAlphaReference) {
        IrisGlDiagnostics.logCeleritasTerrainState(passName, program, hasAlphaTestOverride, expectedAlphaReference);
    }

    @Override
    public void logCloudControlPixels(String stage, String subject, RenderTargets renderTargets) {
        IrisGlDiagnostics.logCloudControlPixels(stage, subject, renderTargets);
    }

    @Override
    public void logCloudTerrainChainPixels(String stage, String subject, RenderTargets renderTargets, int dhDepthTex,
        int dhDepthTex1) {
        IrisGlDiagnostics.logCloudTerrainChainPixels(stage, subject, renderTargets, dhDepthTex, dhDepthTex1);
    }

    @Override
    public void logCompositeChainPixels(String stage, String subject, RenderTargets renderTargets) {
        IrisGlDiagnostics.logCompositeChainPixels(stage, subject, renderTargets);
    }

    @Override
    public void logCompositeDepthPixels(String stage, String subject, RenderTargets renderTargets, int dhDepthTex,
        int dhDepthTex1) {
        IrisGlDiagnostics.logCompositeDepthPixels(stage, subject, renderTargets, dhDepthTex, dhDepthTex1);
    }

    @Override
    public void logCompositeOutputTiming(long cpuTotalNanos, long cpuCenterDepthNanos, long cpuCompositeNanos,
        long cpuFinalNanos, long gpuTotalNanos, long gpuCompositeNanos, long gpuFinalNanos) {
        IrisGlDiagnostics.logCompositeOutputTiming(cpuTotalNanos, cpuCenterDepthNanos, cpuCompositeNanos,
            cpuFinalNanos, gpuTotalNanos, gpuCompositeNanos, gpuFinalNanos);
    }

    @Override
    public void logCurrentFramebufferSamples(String label, int localColorAttachments) {
        IrisGlDiagnostics.logCurrentFramebufferSamples(label, localColorAttachments);
    }

    @Override
    public void logDebugInfo(String message, Object... params) {
        IrisGlDiagnostics.logDebugInfo(message, params);
    }

    @Override
    public void logFrameOutputTiming(long cpuNanos, long gpuNanos) {
        IrisGlDiagnostics.logFrameOutputTiming(cpuNanos, gpuNanos);
    }

    @Override
    public void logFrameRenderTiming(long cpuNanos, long gpuNanos) {
        IrisGlDiagnostics.logFrameRenderTiming(cpuNanos, gpuNanos);
    }

    @Override
    public void logFramebufferOutputState(String label, int framebufferTexture, int framebufferWidth,
        int framebufferHeight, int framebufferTextureWidth, int framebufferTextureHeight, int outputWidth,
        int outputHeight, boolean disableBlend) {
        IrisGlDiagnostics.logFramebufferOutputState(label, framebufferTexture, framebufferWidth, framebufferHeight,
            framebufferTextureWidth, framebufferTextureHeight, outputWidth, outputHeight, disableBlend);
    }

    @Override
    public void logFullscreenPassState(String stageName, String sourceName, int program, int[] drawBuffers,
        java.util.Set<Integer> readsFromAlt, RenderTargets renderTargets) {
        IrisGlDiagnostics.logFullscreenPassState(stageName, sourceName, program, drawBuffers, readsFromAlt,
            renderTargets);
    }

    @Override
    public void logFullscreenProgram(String stageName, String sourceName, int program, int[] drawBuffers) {
        IrisGlDiagnostics.logFullscreenProgram(stageName, sourceName, program, drawBuffers);
    }

    @Override
    public void logFullscreenSamplerSamples(String stageName, String sourceName, int program) {
        IrisGlDiagnostics.logFullscreenSamplerSamples(stageName, sourceName, program);
    }

    @Override
    public void logModProgramOverride(String stage, String phase, String inputs, boolean shadow, boolean mainBound,
        boolean renderingWorld, boolean fullscreen, boolean postChain, int previousProgram, int activePassProgram) {
        IrisGlDiagnostics.logModProgramOverride(stage, phase, inputs, shadow, mainBound, renderingWorld, fullscreen,
            postChain, previousProgram, activePassProgram);
    }

    @Override
    public void logPassBind(String stage, String phase, int previousProgram, int nextProgram) {
        IrisGlDiagnostics.logPassBind(stage, phase, previousProgram, nextProgram);
    }

    @Override
    public void logPhaseChange(String stage, String previousPhase, String nextPhase, boolean shadow,
        boolean mainBound, boolean renderingWorld, boolean fullscreen, boolean postChain, String inputs) {
        IrisGlDiagnostics.logPhaseChange(stage, previousPhase, nextPhase, shadow, mainBound, renderingWorld,
            fullscreen, postChain, inputs);
    }

    @Override
    public void logPipelineInputs(String stage, String phase, String availability, boolean shadow, boolean mainBound,
        boolean fullscreen, boolean postChain) {
        IrisGlDiagnostics.logPipelineInputs(stage, phase, availability, shadow, mainBound, fullscreen, postChain);
    }

    @Override
    public void logPipelineMatch(String stage, String phase, String condition, boolean shadow, boolean mainBound,
        boolean renderingWorld, boolean fullscreen, boolean postChain, int program) {
        IrisGlDiagnostics.logPipelineMatch(stage, phase, condition, shadow, mainBound, renderingWorld, fullscreen,
            postChain, program);
    }

    @Override
    public void logPipelineSkip(String stage, String phase, boolean shadow, boolean mainBound, boolean renderingWorld,
        boolean fullscreen, boolean postChain) {
        IrisGlDiagnostics.logPipelineSkip(stage, phase, shadow, mainBound, renderingWorld, fullscreen, postChain);
    }

    @Override
    public void logProgramOverrideDecision(String stage, String phase, int oldProgram, int newProgram,
        int activePassProgram, boolean shouldOverrideShaders, boolean renderingLevel, boolean ownedProgram,
        boolean unlockedDepthColor, boolean invokedOverride) {
        IrisGlDiagnostics.logProgramOverrideDecision(stage, phase, oldProgram, newProgram, activePassProgram,
            shouldOverrideShaders, renderingLevel, ownedProgram, unlockedDepthColor, invokedOverride);
    }

    @Override
    public void logProgramSamplerState(String stage, int program, String availability, String phase) {
        IrisGlDiagnostics.logProgramSamplerState(stage, program, availability, phase);
    }

    @Override
    public void logSamplerInitialization(int program, String mode, String name, int location, int assignedUnit) {
        IrisGlDiagnostics.logSamplerInitialization(program, mode, name, location, assignedUnit);
    }

    @Override
    public void logSamplerIntercept(int program, String mode, int requestedUnit, boolean override, String... names) {
        IrisGlDiagnostics.logSamplerIntercept(program, mode, requestedUnit, override, names);
    }

    @Override
    public void logShadowEntityState(String stage, double cameraX, double cameraY, double cameraZ, double renderPosX,
        double renderPosY, double renderPosZ, double viewerPosX, double viewerPosY, double viewerPosZ, int entityCount) {
        IrisGlDiagnostics.logShadowEntityState(stage, cameraX, cameraY, cameraZ, renderPosX, renderPosY, renderPosZ,
            viewerPosX, viewerPosY, viewerPosZ, entityCount);
    }

    @Override
    public void logShadowPassState(String stage, boolean terrain, boolean translucent, boolean entities,
        boolean player, boolean blockEntities, int visibleChunks, int renderedEntities, int renderedBlockEntities) {
        IrisGlDiagnostics.logShadowPassState(stage, terrain, translucent, entities, player, blockEntities,
            visibleChunks, renderedEntities, renderedBlockEntities);
    }

    @Override
    public void logTerrainMaterialSample(String source, int blockId, int renderType, int lightValue, int localX,
        int localY, int localZ, float u, float v) {
        IrisGlDiagnostics.logTerrainMaterialSample(source, blockId, renderType, lightValue, localX, localY, localZ, u,
            v);
    }

    @Override
    public void logWhiteScreenProbe(String label) {
        IrisGlDiagnostics.logWhiteScreenProbe(label);
    }

    @Override
    public void logWorldPassState(String stage, String phase, String subject) {
        IrisGlDiagnostics.logWorldPassState(stage, phase, subject);
    }

    @Override
    public void markStage(String stage) {
        IrisGlDiagnostics.markStage(stage);
    }

    @Override
    public void recordGameLoopStageTiming(String stage, long startNanos) {
        IrisGlDiagnostics.recordGameLoopStageTiming(stage, startNanos);
    }

    @Override
    public void recordWorldPassStage(String nextStage) {
        IrisGlDiagnostics.recordWorldPassStage(nextStage);
    }

    @Override
    public String replaceFramebufferSamplePhase(String phase) {
        return IrisGlDiagnostics.replaceFramebufferSamplePhase(phase);
    }

    @Override
    public void restoreFramebufferSamplePhase(String phase) {
        IrisGlDiagnostics.restoreFramebufferSamplePhase(phase);
    }

    @Override
    public boolean shouldCaptureGpuPerfTiming() {
        return IrisGlDiagnostics.shouldCaptureGpuPerfTiming();
    }

    @Override
    public boolean shouldLogGlsmEvent(String label, int maxCount) {
        return IrisGlDiagnostics.shouldLogGlsmEvent(label, maxCount);
    }

    @Override
    public boolean shouldLogPortalRenderEvents() {
        return IrisGlDiagnostics.shouldLogPortalRenderEvents();
    }

    @Override
    public boolean shouldLogTextureUnitEvents() {
        return IrisGlDiagnostics.shouldLogTextureUnitEvents();
    }
}
