package com.demonica.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemonicaRuntimeOptionsTest {
    private static final String PERF_DEBUG_PROPERTY = "demonica.perfDebug";
    private static final String PBR_DEBUG_PROPERTY = "demonica.pbrDebug";
    private final String originalPerfDebugProperty = System.getProperty(PERF_DEBUG_PROPERTY);
    private final String originalPbrDebugProperty = System.getProperty(PBR_DEBUG_PROPERTY);

    @AfterEach
    void restoreDebugProperties() {
        restoreProperty(PERF_DEBUG_PROPERTY, this.originalPerfDebugProperty);
        restoreProperty(PBR_DEBUG_PROPERTY, this.originalPbrDebugProperty);
    }

    @Test
    void usesConfiguredValueWithoutAnExplicitOverride() {
        System.clearProperty(PERF_DEBUG_PROPERTY);

        assertTrue(DemonicaRuntimeOptions.resolvePerfDebugEnabled(true));
        assertFalse(DemonicaRuntimeOptions.resolvePerfDebugEnabled(false));
    }

    @Test
    void explicitOverrideTakesPriorityOverConfiguredValue() {
        System.setProperty(PERF_DEBUG_PROPERTY, "true");
        assertTrue(DemonicaRuntimeOptions.resolvePerfDebugEnabled(false));

        System.setProperty(PERF_DEBUG_PROPERTY, "false");
        assertFalse(DemonicaRuntimeOptions.resolvePerfDebugEnabled(true));
    }

    @Test
    void usesConfiguredPbrDebugValueWithoutAnExplicitOverride() {
        System.clearProperty(PBR_DEBUG_PROPERTY);

        assertTrue(DemonicaRuntimeOptions.resolvePbrDebugEnabled(true));
        assertFalse(DemonicaRuntimeOptions.resolvePbrDebugEnabled(false));
    }

    @Test
    void explicitPbrDebugOverrideTakesPriorityOverConfiguredValue() {
        System.setProperty(PBR_DEBUG_PROPERTY, "true");
        assertTrue(DemonicaRuntimeOptions.resolvePbrDebugEnabled(false));

        System.setProperty(PBR_DEBUG_PROPERTY, "false");
        assertFalse(DemonicaRuntimeOptions.resolvePbrDebugEnabled(true));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
