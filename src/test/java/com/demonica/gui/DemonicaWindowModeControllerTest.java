package com.demonica.gui;

import com.demonica.config.DemonicaOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemonicaWindowModeControllerTest {
    @Test
    void resolvesStoredFullscreenModes() {
        DemonicaOptions options = new DemonicaOptions();

        options.window.fullscreenMode = FullscreenMode.BORDERLESS.name();
        assertEquals(FullscreenMode.BORDERLESS, DemonicaWindowModeController.resolveConfiguredMode(options));

        options.window.fullscreenMode = FullscreenMode.EXCLUSIVE.name();
        assertEquals(FullscreenMode.EXCLUSIVE, DemonicaWindowModeController.resolveConfiguredMode(options));

        options.window.fullscreenMode = FullscreenMode.OFF.name();
        assertEquals(FullscreenMode.OFF, DemonicaWindowModeController.resolveConfiguredMode(options));
    }

    @Test
    void fallsBackToWindowedForMissingOrInvalidMode() {
        DemonicaOptions options = new DemonicaOptions();

        assertEquals(FullscreenMode.OFF, DemonicaWindowModeController.resolveConfiguredMode(options));

        options.window.fullscreenMode = "invalid-mode";
        assertEquals(FullscreenMode.OFF, DemonicaWindowModeController.resolveConfiguredMode(options));
    }

    @Test
    void migratesLegacyFullscreenModeToExclusive() {
        DemonicaOptions options = new DemonicaOptions();
        options.window.fullscreenMode = "FULLSCREEN";

        assertEquals(FullscreenMode.EXCLUSIVE, DemonicaWindowModeController.resolveConfiguredMode(options));
        assertEquals(FullscreenMode.EXCLUSIVE.name(), options.window.fullscreenMode);
    }

    @Test
    void togglesBetweenWindowedAndExclusiveFullscreen() {
        assertEquals(
            FullscreenMode.EXCLUSIVE,
            DemonicaWindowModeController.nextMode(FullscreenMode.OFF, FullscreenMode.EXCLUSIVE)
        );
        assertEquals(
            FullscreenMode.OFF,
            DemonicaWindowModeController.nextMode(FullscreenMode.EXCLUSIVE, FullscreenMode.EXCLUSIVE)
        );
        assertEquals(
            FullscreenMode.OFF,
            DemonicaWindowModeController.nextMode(FullscreenMode.BORDERLESS, FullscreenMode.BORDERLESS)
        );
    }

    @Test
    void togglesBackToLastBorderlessFullscreen() {
        assertEquals(
            FullscreenMode.BORDERLESS,
            DemonicaWindowModeController.nextMode(FullscreenMode.OFF, FullscreenMode.BORDERLESS)
        );
    }

    @Test
    void resolvesLastFullscreenMode() {
        DemonicaOptions options = new DemonicaOptions();
        assertEquals(FullscreenMode.EXCLUSIVE, DemonicaWindowModeController.resolveLastFullscreenMode(options));

        options.window.lastFullscreenMode = FullscreenMode.BORDERLESS.name();
        assertEquals(FullscreenMode.BORDERLESS, DemonicaWindowModeController.resolveLastFullscreenMode(options));

        options.window.lastFullscreenMode = FullscreenMode.OFF.name();
        assertEquals(FullscreenMode.EXCLUSIVE, DemonicaWindowModeController.resolveLastFullscreenMode(options));
    }

    @Test
    void resolvesLastFullscreenModeFromCurrentSelection() {
        DemonicaOptions options = new DemonicaOptions();
        options.window.fullscreenMode = FullscreenMode.BORDERLESS.name();

        assertEquals(FullscreenMode.BORDERLESS, DemonicaWindowModeController.resolveLastFullscreenMode(options));
    }

    @Test
    void degradesBorderlessToExclusiveWhenUnsupported() {
        assertEquals(
            FullscreenMode.EXCLUSIVE,
            DemonicaWindowModeController.effectiveMode(FullscreenMode.BORDERLESS, false)
        );
        assertEquals(
            FullscreenMode.BORDERLESS,
            DemonicaWindowModeController.effectiveMode(FullscreenMode.BORDERLESS, true)
        );
        assertEquals(
            FullscreenMode.EXCLUSIVE,
            DemonicaWindowModeController.effectiveMode(FullscreenMode.EXCLUSIVE, false)
        );
        assertEquals(
            FullscreenMode.OFF,
            DemonicaWindowModeController.effectiveMode(FullscreenMode.OFF, false)
        );
    }
}
