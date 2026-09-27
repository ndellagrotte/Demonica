package net.coderbot.iris.debug;

import com.gtnewhorizons.angelica.glsm.hooks.GpuCommandRecorder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Whether the diagnostics jar is installed, and the one place that installs it. The first caller, normally GLSM's
 * initialization on the render thread (MixinOpenGlHelper), looks it up; everything after that sees the same answer.
 */
public final class Diagnostics {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDiagnostics");

    private static boolean loaded;
    private static @Nullable DiagnosticsProvider provider;

    private Diagnostics() {
    }

    /** Looks up the diagnostics jar and installs it, once. */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Iterator<DiagnosticsProvider> providers =
                ServiceLoader.load(DiagnosticsProvider.class, DiagnosticsProvider.class.getClassLoader()).iterator();
            if (!providers.hasNext()) {
                return;
            }
            DiagnosticsProvider found = providers.next();
            if (found.install()) {
                provider = found;
                LOGGER.info("Diagnostics jar {} installed", found.version());
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            LOGGER.error("The diagnostics jar could not be loaded; the diagnostics stay off", e);
        }
    }

    /** Whether the diagnostics jar is installed and in use. */
    public static boolean present() {
        load();
        return provider != null;
    }

    /** The diagnostics jar's version, or null without it. */
    public static @Nullable String version() {
        load();
        return provider != null ? provider.version() : null;
    }

    /** The GPU command recorder GLSM is initialized with, or null. */
    public static @Nullable GpuCommandRecorder gpuCommandRecorder() {
        load();
        return provider != null ? provider.gpuCommandRecorder() : null;
    }
}
