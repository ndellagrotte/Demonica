package com.demonica.config;

import com.demonica.runtime.DemonicaRuntime;

import java.util.function.Predicate;

public final class DemonicaRuntimeOptions {
    private static final String ALLOW_DIRECT_MEMORY_ACCESS_PROPERTY = "demonica.allowDirectMemoryAccess";
    private static final String PERF_DEBUG_PROPERTY = "demonica.perfDebug";
    private static final String PBR_DEBUG_PROPERTY = "demonica.pbrDebug";
    private static final String GL_DEBUG_PROPERTY = "demonica.glDebug";
    private static final String CLOUD_CONTROL_DEBUG_PROPERTY = "demonica.cloudControlDebug";
    private static final String GPU_PERF_DEBUG_PROPERTY = "demonica.gpuPerfDebug";
    private static final String PRE_RENDER_GL_ERROR_CHECK_PROPERTY = "demonica.frameGlErrorCheck";
    private static final String POST_RENDER_GL_ERROR_CHECK_PROPERTY = "demonica.postRenderGlErrorCheck";

    private DemonicaRuntimeOptions() {
    }

    public static boolean allowDirectMemoryAccess() {
        String override = System.getProperty(ALLOW_DIRECT_MEMORY_ACCESS_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().advanced.allowDirectMemoryAccess;
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static boolean resolvePerfDebugEnabled(boolean configuredEnabled) {
        String override = System.getProperty(PERF_DEBUG_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        return configuredEnabled;
    }

    public static boolean pbrDebugEnabled() {
        try {
            return resolvePbrDebugEnabled(DemonicaRuntime.options().debug.enablePbrDebug);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean resolvePbrDebugEnabled(boolean configuredEnabled) {
        String override = System.getProperty(PBR_DEBUG_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        return configuredEnabled;
    }

    /** The GL debug logs (diagnostics jar): -Ddemonica.glDebug, otherwise the option. */
    public static boolean glDebugEnabled() {
        return resolve(GL_DEBUG_PROPERTY, options -> options.debug.enableGlDebug);
    }

    /** The cloud-control pixel logs (diagnostics jar): -Ddemonica.cloudControlDebug, otherwise the option. */
    public static boolean cloudControlDebugEnabled() {
        return resolve(CLOUD_CONTROL_DEBUG_PROPERTY, options -> options.debug.enableCloudControlDebug);
    }

    /** CPU timing tables: -Ddemonica.perfDebug, otherwise the option. */
    public static boolean perfDebugEnabled() {
        return resolve(PERF_DEBUG_PROPERTY, options -> options.debug.enablePerfDebug);
    }

    /** GPU timer queries (diagnostics jar): -Ddemonica.gpuPerfDebug, otherwise the option. */
    public static boolean gpuPerfDebugEnabled() {
        return resolve(GPU_PERF_DEBUG_PROPERTY, options -> options.debug.enableGpuPerfDebug);
    }

    /** Whether vanilla's "Pre render" glGetError check runs: -Ddemonica.frameGlErrorCheck, otherwise the option. */
    public static boolean checkPreRenderGlErrors() {
        return resolve(PRE_RENDER_GL_ERROR_CHECK_PROPERTY, options -> options.debug.enableFrameGlErrorCheck);
    }

    /** Whether vanilla's "Post render" glGetError check runs: -Ddemonica.postRenderGlErrorCheck, otherwise the option. */
    public static boolean checkPostRenderGlErrors() {
        return resolve(POST_RENDER_GL_ERROR_CHECK_PROPERTY, options -> options.debug.enablePostRenderGlErrorCheck);
    }

    private static boolean resolve(String property, Predicate<DemonicaOptions> configured) {
        String override = System.getProperty(property);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return configured.test(DemonicaRuntime.options());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
