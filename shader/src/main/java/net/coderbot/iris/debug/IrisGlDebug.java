package net.coderbot.iris.debug;

import net.coderbot.iris.rendertarget.RenderTargets;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute;

import java.util.Collection;

/**
 * The GL debug logs and timing tables of the Iris pipeline, as called from the pipeline and from Demonica's mixins.
 * Every method is a no-op until the diagnostics jar installs its implementation ({@link Diagnostics}); the
 * implementation is com.demonica.diagnostics.iris.IrisGlDiagnostics. Calls that must build a string or query GL for
 * their arguments are guarded by {@link #active()}.
 */
public final class IrisGlDebug {
    /** The implementation's side. Each default is what the pipeline sees without the diagnostics jar. */
    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default void beginFramebufferSamplePhase(String phase) {
        }

        default void check(String stage) {
        }

        default void endFramebufferSamplePhase() {
        }

        default boolean isCloudControlDebugEnabled() {
            return false;
        }

        default void logCeleritasProgram(String passName, int program, Collection<GlVertexAttribute> attributes) {
        }

        default void logCeleritasTerrainState(String passName, int program, boolean hasAlphaTestOverride,
            float expectedAlphaReference) {
        }

        default void logCloudControlPixels(String stage, String subject, RenderTargets renderTargets) {
        }

        default void logCloudTerrainChainPixels(String stage, String subject, RenderTargets renderTargets,
            int dhDepthTex, int dhDepthTex1) {
        }

        default void logCompositeChainPixels(String stage, String subject, RenderTargets renderTargets) {
        }

        default void logCompositeDepthPixels(String stage, String subject, RenderTargets renderTargets,
            int dhDepthTex, int dhDepthTex1) {
        }

        default void logCompositeOutputTiming(long cpuTotalNanos, long cpuCenterDepthNanos, long cpuCompositeNanos,
            long cpuFinalNanos, long gpuTotalNanos, long gpuCompositeNanos, long gpuFinalNanos) {
        }

        default void logCurrentFramebufferSamples(String label, int localColorAttachments) {
        }

        default void logDebugInfo(String message, Object... params) {
        }

        default void logFullscreenPassState(String stageName, String sourceName, int program, int[] drawBuffers,
            java.util.Set<Integer> readsFromAlt, RenderTargets renderTargets) {
        }

        default void logFullscreenProgram(String stageName, String sourceName, int program, int[] drawBuffers) {
        }

        default void logFullscreenSamplerSamples(String stageName, String sourceName, int program) {
        }

        default void logModProgramOverride(String stage, String phase, String inputs, boolean shadow,
            boolean mainBound, boolean renderingWorld, boolean fullscreen, boolean postChain, int previousProgram,
            int activePassProgram) {
        }

        default void logPassBind(String stage, String phase, int previousProgram, int nextProgram) {
        }

        default void logPhaseChange(String stage, String previousPhase, String nextPhase, boolean shadow,
            boolean mainBound, boolean renderingWorld, boolean fullscreen, boolean postChain, String inputs) {
        }

        default void logPipelineInputs(String stage, String phase, String availability, boolean shadow,
            boolean mainBound, boolean fullscreen, boolean postChain) {
        }

        default void logPipelineMatch(String stage, String phase, String condition, boolean shadow, boolean mainBound,
            boolean renderingWorld, boolean fullscreen, boolean postChain, int program) {
        }

        default void logPipelineSkip(String stage, String phase, boolean shadow, boolean mainBound,
            boolean renderingWorld, boolean fullscreen, boolean postChain) {
        }

        default void logProgramOverrideDecision(String stage, String phase, int oldProgram, int newProgram,
            int activePassProgram, boolean shouldOverrideShaders, boolean renderingLevel, boolean ownedProgram,
            boolean unlockedDepthColor, boolean invokedOverride) {
        }

        default void logProgramSamplerState(String stage, int program, String availability, String phase) {
        }

        default void logSamplerInitialization(int program, String mode, String name, int location, int assignedUnit) {
        }

        default void logSamplerIntercept(int program, String mode, int requestedUnit, boolean override, String... names) {
        }

        default void logShadowEntityState(String stage, double cameraX, double cameraY, double cameraZ,
            double renderPosX, double renderPosY, double renderPosZ, double viewerPosX, double viewerPosY,
            double viewerPosZ, int entityCount) {
        }

        default void logShadowPassState(String stage, boolean terrain, boolean translucent, boolean entities,
            boolean player, boolean blockEntities, int visibleChunks, int renderedEntities, int renderedBlockEntities) {
        }

        default void logTerrainMaterialSample(String source, int blockId, int renderType, int lightValue, int localX,
            int localY, int localZ, float u, float v) {
        }

        default void logWorldPassState(String stage, String phase, String subject) {
        }

        default void markStage(String stage) {
        }

        default void recordWorldPassStage(String nextStage) {
        }

        default String replaceFramebufferSamplePhase(String phase) {
            return null;
        }

        default void restoreFramebufferSamplePhase(String phase) {
        }

        default boolean shouldCaptureGpuPerfTiming() {
            return false;
        }

        default boolean shouldLogGlsmEvent(String label, int maxCount) {
            return false;
        }

        default boolean shouldLogPortalRenderEvents() {
            return false;
        }

        default boolean shouldLogTextureUnitEvents() {
            return false;
        }
    }

    private static Hooks hooks = Hooks.NOOP;

    private IrisGlDebug() {
    }

    /** Installs the implementation. Called once, on the render thread, by the diagnostics jar. */
    public static void install(Hooks implementation) {
        hooks = implementation != null ? implementation : Hooks.NOOP;
    }

    /** Whether the diagnostics jar is installed; a cheap guard for arguments that cost something to build. */
    public static boolean active() {
        return hooks != Hooks.NOOP;
    }

    public static void beginFramebufferSamplePhase(String phase) {
        hooks.beginFramebufferSamplePhase(phase);
    }

    public static void check(String stage) {
        hooks.check(stage);
    }

    public static void endFramebufferSamplePhase() {
        hooks.endFramebufferSamplePhase();
    }

    public static boolean isCloudControlDebugEnabled() {
        return hooks.isCloudControlDebugEnabled();
    }

    public static void logCeleritasProgram(String passName, int program, Collection<GlVertexAttribute> attributes) {
        hooks.logCeleritasProgram(passName, program, attributes);
    }

    public static void logCeleritasTerrainState(String passName, int program, boolean hasAlphaTestOverride,
        float expectedAlphaReference) {
        hooks.logCeleritasTerrainState(passName, program, hasAlphaTestOverride, expectedAlphaReference);
    }

    public static void logCloudControlPixels(String stage, String subject, RenderTargets renderTargets) {
        hooks.logCloudControlPixels(stage, subject, renderTargets);
    }

    public static void logCloudTerrainChainPixels(String stage, String subject, RenderTargets renderTargets,
        int dhDepthTex, int dhDepthTex1) {
        hooks.logCloudTerrainChainPixels(stage, subject, renderTargets, dhDepthTex, dhDepthTex1);
    }

    public static void logCompositeChainPixels(String stage, String subject, RenderTargets renderTargets) {
        hooks.logCompositeChainPixels(stage, subject, renderTargets);
    }

    public static void logCompositeDepthPixels(String stage, String subject, RenderTargets renderTargets,
        int dhDepthTex, int dhDepthTex1) {
        hooks.logCompositeDepthPixels(stage, subject, renderTargets, dhDepthTex, dhDepthTex1);
    }

    public static void logCompositeOutputTiming(long cpuTotalNanos, long cpuCenterDepthNanos, long cpuCompositeNanos,
        long cpuFinalNanos, long gpuTotalNanos, long gpuCompositeNanos, long gpuFinalNanos) {
        hooks.logCompositeOutputTiming(cpuTotalNanos, cpuCenterDepthNanos, cpuCompositeNanos, cpuFinalNanos,
            gpuTotalNanos, gpuCompositeNanos, gpuFinalNanos);
    }

    public static void logCurrentFramebufferSamples(String label, int localColorAttachments) {
        hooks.logCurrentFramebufferSamples(label, localColorAttachments);
    }

    public static void logDebugInfo(String message, Object... params) {
        hooks.logDebugInfo(message, params);
    }

    public static void logFullscreenPassState(String stageName, String sourceName, int program, int[] drawBuffers,
        java.util.Set<Integer> readsFromAlt, RenderTargets renderTargets) {
        hooks.logFullscreenPassState(stageName, sourceName, program, drawBuffers, readsFromAlt, renderTargets);
    }

    public static void logFullscreenProgram(String stageName, String sourceName, int program, int[] drawBuffers) {
        hooks.logFullscreenProgram(stageName, sourceName, program, drawBuffers);
    }

    public static void logFullscreenSamplerSamples(String stageName, String sourceName, int program) {
        hooks.logFullscreenSamplerSamples(stageName, sourceName, program);
    }

    public static void logModProgramOverride(String stage, String phase, String inputs, boolean shadow,
        boolean mainBound, boolean renderingWorld, boolean fullscreen, boolean postChain, int previousProgram,
        int activePassProgram) {
        hooks.logModProgramOverride(stage, phase, inputs, shadow, mainBound, renderingWorld, fullscreen, postChain,
            previousProgram, activePassProgram);
    }

    public static void logPassBind(String stage, String phase, int previousProgram, int nextProgram) {
        hooks.logPassBind(stage, phase, previousProgram, nextProgram);
    }

    public static void logPhaseChange(String stage, String previousPhase, String nextPhase, boolean shadow,
        boolean mainBound, boolean renderingWorld, boolean fullscreen, boolean postChain, String inputs) {
        hooks.logPhaseChange(stage, previousPhase, nextPhase, shadow, mainBound, renderingWorld, fullscreen,
            postChain, inputs);
    }

    public static void logPipelineInputs(String stage, String phase, String availability, boolean shadow,
        boolean mainBound, boolean fullscreen, boolean postChain) {
        hooks.logPipelineInputs(stage, phase, availability, shadow, mainBound, fullscreen, postChain);
    }

    public static void logPipelineMatch(String stage, String phase, String condition, boolean shadow,
        boolean mainBound, boolean renderingWorld, boolean fullscreen, boolean postChain, int program) {
        hooks.logPipelineMatch(stage, phase, condition, shadow, mainBound, renderingWorld, fullscreen, postChain,
            program);
    }

    public static void logPipelineSkip(String stage, String phase, boolean shadow, boolean mainBound,
        boolean renderingWorld, boolean fullscreen, boolean postChain) {
        hooks.logPipelineSkip(stage, phase, shadow, mainBound, renderingWorld, fullscreen, postChain);
    }

    public static void logProgramOverrideDecision(String stage, String phase, int oldProgram, int newProgram,
        int activePassProgram, boolean shouldOverrideShaders, boolean renderingLevel, boolean ownedProgram,
        boolean unlockedDepthColor, boolean invokedOverride) {
        hooks.logProgramOverrideDecision(stage, phase, oldProgram, newProgram, activePassProgram,
            shouldOverrideShaders, renderingLevel, ownedProgram, unlockedDepthColor, invokedOverride);
    }

    public static void logProgramSamplerState(String stage, int program, String availability, String phase) {
        hooks.logProgramSamplerState(stage, program, availability, phase);
    }

    public static void logSamplerInitialization(int program, String mode, String name, int location, int assignedUnit) {
        hooks.logSamplerInitialization(program, mode, name, location, assignedUnit);
    }

    public static void logSamplerIntercept(int program, String mode, int requestedUnit, boolean override,
        String... names) {
        hooks.logSamplerIntercept(program, mode, requestedUnit, override, names);
    }

    public static void logShadowEntityState(String stage, double cameraX, double cameraY, double cameraZ,
        double renderPosX, double renderPosY, double renderPosZ, double viewerPosX, double viewerPosY,
        double viewerPosZ, int entityCount) {
        hooks.logShadowEntityState(stage, cameraX, cameraY, cameraZ, renderPosX, renderPosY, renderPosZ, viewerPosX,
            viewerPosY, viewerPosZ, entityCount);
    }

    public static void logShadowPassState(String stage, boolean terrain, boolean translucent, boolean entities,
        boolean player, boolean blockEntities, int visibleChunks, int renderedEntities, int renderedBlockEntities) {
        hooks.logShadowPassState(stage, terrain, translucent, entities, player, blockEntities, visibleChunks,
            renderedEntities, renderedBlockEntities);
    }

    public static void logTerrainMaterialSample(String source, int blockId, int renderType, int lightValue,
        int localX, int localY, int localZ, float u, float v) {
        hooks.logTerrainMaterialSample(source, blockId, renderType, lightValue, localX, localY, localZ, u, v);
    }

    public static void logWorldPassState(String stage, String phase, String subject) {
        hooks.logWorldPassState(stage, phase, subject);
    }

    public static void markStage(String stage) {
        hooks.markStage(stage);
    }

    public static void recordWorldPassStage(String nextStage) {
        hooks.recordWorldPassStage(nextStage);
    }

    public static String replaceFramebufferSamplePhase(String phase) {
        return hooks.replaceFramebufferSamplePhase(phase);
    }

    public static void restoreFramebufferSamplePhase(String phase) {
        hooks.restoreFramebufferSamplePhase(phase);
    }

    public static boolean shouldCaptureGpuPerfTiming() {
        return hooks.shouldCaptureGpuPerfTiming();
    }

    public static boolean shouldLogGlsmEvent(String label, int maxCount) {
        return hooks.shouldLogGlsmEvent(label, maxCount);
    }

    public static boolean shouldLogPortalRenderEvents() {
        return hooks.shouldLogPortalRenderEvents();
    }

    public static boolean shouldLogTextureUnitEvents() {
        return hooks.shouldLogTextureUnitEvents();
    }
}
