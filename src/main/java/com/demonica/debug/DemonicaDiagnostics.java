package com.demonica.debug;

import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.mixins.MixinEarly;
import com.demonica.runtime.DemonicaRuntime;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DemonicaDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDiagnostics");
    private static final Set<String> APPLIED_MIXINS = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Set<String> GIBBED_RENDER_PATHS = Collections.synchronizedSet(new LinkedHashSet<>());

    private DemonicaDiagnostics() {
    }

    public static boolean isEnabled() {
        String override = System.getProperty("demonica.productionDiagnostics");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        try {
            return DemonicaRuntime.options().debug.enableProductionDiagnostics;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    public static void logConstruction() {
        if (!isEnabled()) {
            return;
        }

        LOGGER.info(
            "construction env={} side=client java={} os={} coremod={} mixinConfigs={}",
            isDeobfuscatedEnvironment() ? "dev" : "production",
            System.getProperty("java.version"),
            System.getProperty("os.name") + " " + System.getProperty("os.version"),
            System.getProperty("fml.coreMods.load", ""),
            describeMixinConfigs()
        );
    }

    public static void logInitialization(String version) {
        if (!isEnabled()) {
            return;
        }

        LOGGER.info("init version={} config={}", version, describeConfig());
    }

    public static void recordMixinApplied(String targetClassName, String mixinClassName) {
        if (!isEnabled() || !isKeyMixin(mixinClassName)) {
            return;
        }

        String entry = mixinClassName + " -> " + targetClassName;
        if (APPLIED_MIXINS.add(entry)) {
            LOGGER.info("mixin-applied {}", entry);
        }
    }

    public static void recordGibbedRenderPath(String path) {
        if (!isEnabled()) {
            return;
        }

        if (GIBBED_RENDER_PATHS.add(path)) {
            LOGGER.info("gibbed-render-path {}", path);
        }
    }

    private static boolean isKeyMixin(String mixinClassName) {
        return mixinClassName.startsWith("com.demonica.mixin.features.iris.")
            || mixinClassName.startsWith("com.demonica.mixin.mod.gibbed.")
            || mixinClassName.startsWith("com.demonica.mixin.core.")
            || mixinClassName.startsWith("com.demonica.mixin.celeritas.");
    }

    private static String describeMixinConfigs() {
        return String.join(",", MixinEarly.getEarlyMixinConfigs());
    }

    private static String describeConfig() {
        try {
            var options = DemonicaRuntime.options();
            return "advanced{streaming=" + options.advanced.streamingUploadStrategy
                + ",directMemory=" + DemonicaRuntimeOptions.allowDirectMemoryAccess()
                + ",modelRendererBatching=" + DemonicaRuntimeOptions.useModelRendererBatching()
                + ",modelRendererDisplayLists=" + DemonicaRuntimeOptions.useModelRendererDisplayLists()
                + ",fastLitItemRendering=" + DemonicaRuntimeOptions.useFastLitItemRendering()
                + ",fastLitItemDisplayLists=" + DemonicaRuntimeOptions.useFastLitItemDisplayLists()
                + "} debug{gl=" + options.debug.enableGlDebug
                + ",perf=" + options.debug.enablePerfDebug
                + ",gpuPerf=" + options.debug.enableGpuPerfDebug
                + ",pbr=" + DemonicaRuntimeOptions.pbrDebugEnabled()
                + ",preGlError=" + options.debug.enableFrameGlErrorCheck
                + ",postGlError=" + options.debug.enablePostRenderGlErrorCheck
                + ",diagnostics=" + options.debug.enableProductionDiagnostics
                + ",redirectorDebug=" + options.debug.enableRedirectorDebug
                + ",redirectorLogSpam=" + options.debug.enableRedirectorLogSpam
                + ",redirectorClassDump=" + options.debug.enableRedirectorClassDump
                + "}";
        } catch (RuntimeException e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }

    private static boolean isDeobfuscatedEnvironment() {
        Object value = Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return value instanceof Boolean && (Boolean) value;
    }
}
