package com.demonica.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemonicaStartupDebugConfigTest {
    @Test
    void resolvesLwjglDebugFromPropertyOverrideOrConfiguredFallback() {
        assertTrue(DemonicaStartupDebugConfig.resolveLwjglDebug("true", false));
        assertTrue(DemonicaStartupDebugConfig.resolveLwjglDebug("TRUE", false));
        assertFalse(DemonicaStartupDebugConfig.resolveLwjglDebug("false", true));
        assertTrue(DemonicaStartupDebugConfig.resolveLwjglDebug(null, true));
        assertFalse(DemonicaStartupDebugConfig.resolveLwjglDebug(null, false));
    }
}
