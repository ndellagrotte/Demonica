package com.demonica.debug;

import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.mixins.MixinEarly;
import com.demonica.runtime.DemonicaRuntime;
import net.coderbot.iris.debug.Diagnostics;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DemonicaDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDiagnostics");

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

        String diagnosticsJar = Diagnostics.version();
        LOGGER.info("init version={} diagnosticsJar={} config={}", version, diagnosticsJar != null ? diagnosticsJar : "absent",
            describeConfig());
    }

    private static String describeMixinConfigs() {
        return String.join(",", MixinEarly.getEarlyMixinConfigs());
    }

    private static String describeConfig() {
        try {
            var options = DemonicaRuntime.options();
            return "advanced{streaming=" + options.advanced.streamingUploadStrategy
                + ",directMemory=" + DemonicaRuntimeOptions.allowDirectMemoryAccess()
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
