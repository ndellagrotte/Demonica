package com.demonica.config;

import com.demonica.runtime.DemonicaRuntime;

public final class DemonicaRuntimeOptions {
    private static final String ALLOW_DIRECT_MEMORY_ACCESS_PROPERTY = "demonica.allowDirectMemoryAccess";
    private static final String MODEL_RENDERER_BATCHING_PROPERTY = "demonica.modelRendererBatching";
    private static final String MODEL_RENDERER_DISPLAY_LISTS_PROPERTY = "demonica.modelRendererDisplayLists";
    private static final String FAST_LIT_ITEM_RENDERING_PROPERTY = "demonica.fastLitItemRendering";
    private static final String FAST_LIT_ITEM_DISPLAY_LISTS_PROPERTY = "demonica.fastLitItemDisplayLists";
    private static final String DEFERRED_PARTICLE_BATCHING_PROPERTY = "demonica.deferredParticleBatching";
    private static final String PERF_DEBUG_PROPERTY = "demonica.perfDebug";
    private static final String PBR_DEBUG_PROPERTY = "demonica.pbrDebug";

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

    public static boolean useModelRendererBatching() {
        String override = System.getProperty(MODEL_RENDERER_BATCHING_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().advanced.useModelRendererBatching;
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static boolean useModelRendererDisplayLists() {
        String override = System.getProperty(MODEL_RENDERER_DISPLAY_LISTS_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().advanced.useModelRendererDisplayLists;
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static boolean useFastLitItemRendering() {
        String override = System.getProperty(FAST_LIT_ITEM_RENDERING_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().advanced.useFastLitItemRendering;
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static boolean useFastLitItemDisplayLists() {
        String override = System.getProperty(FAST_LIT_ITEM_DISPLAY_LISTS_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().advanced.useFastLitItemDisplayLists;
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static boolean useDeferredParticleBatching() {
        String override = System.getProperty(DEFERRED_PARTICLE_BATCHING_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().advanced.enableDeferredBatching;
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
}
