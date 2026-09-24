package com.demonica.runtime;

import com.demonica.config.DemonicaOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DemonicaRuntime {
    public static final String MODID = "demonica";

    private static final Logger LOGGER = LogManager.getLogger("Demonica");
    private static final DemonicaOptions CONFIG = loadConfig();

    private static volatile String version = "unknown";

    private DemonicaRuntime() {
    }

    public static Logger logger() {
        return LOGGER;
    }

    public static DemonicaOptions options() {
        return CONFIG;
    }

    public static String version() {
        return version;
    }

    public static void setVersion(String version) {
        if (version != null && !version.isBlank()) {
            DemonicaRuntime.version = version;
        }
    }

    private static DemonicaOptions loadConfig() {
        try {
            return DemonicaOptions.load();
        } catch (Throwable t) {
            LOGGER.error("Failed to load configuration file", t);
            LOGGER.error("Using default configuration file in read-only mode");
            DemonicaOptions config = DemonicaOptions.defaults();
            config.setReadOnly();
            return config;
        }
    }
}
