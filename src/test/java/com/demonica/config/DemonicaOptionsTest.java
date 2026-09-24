package com.demonica.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Demonica's settings file, and the one-time takeover of Demonica's settings from Actinium's file. */
class DemonicaOptionsTest {
    private static final String ACTINIUM = """
        {
          "enable_debug_tab": true,
          "quality": { "dynamic_fov": false, "weather_quality": "FANCY", "chunk_fade_in_duration": 500 },
          "performance": { "use_fast_block_renderer": true, "loading_screen_framerate_limit": 90, "chunk_builder_threads": 4 },
          "advanced": { "streaming_upload_strategy": "BUFFER_DATA", "multi_draw_mode": "INDIRECT", "use_fast_lit_item_rendering": false },
          "debug": { "enable_actinium_gl_debug": true, "enable_actinium_perf_debug": true, "enable_redirector_debug": true },
          "window": { "fullscreen_mode": "BORDERLESS", "last_fullscreen_mode": "BORDERLESS" }
        }
        """;

    @Test
    void takesOverDemonicasSettingsFromActiniumOnce(@TempDir Path dir) throws IOException {
        Path actinium = Files.writeString(dir.resolve(DemonicaOptions.ACTINIUM_FILE_NAME), ACTINIUM);

        DemonicaOptions options = DemonicaOptions.load(dir);

        assertTrue(options.enableDebugTab);
        assertFalse(options.quality.dynamicFov);
        assertEquals(90, options.performance.loadingScreenFramerateLimit);
        assertFalse(options.performance.useFastBlockRenderer, "Actinium's fast renderer had gates Demonica has not ported");
        assertEquals(DemonicaOptions.StreamingUploadStrategy.BUFFER_DATA, options.advanced.streamingUploadStrategy);
        assertFalse(options.advanced.useFastLitItemRendering);
        assertTrue(options.debug.enableGlDebug, "renamed from enable_actinium_gl_debug");
        assertTrue(options.debug.enablePerfDebug, "renamed from enable_actinium_perf_debug");
        assertTrue(options.debug.enableRedirectorDebug);
        assertEquals("BORDERLESS", options.window.fullscreenMode);
        assertEquals(ACTINIUM, Files.readString(actinium), "Actinium's file is left alone");

        String written = Files.readString(dir.resolve(DemonicaOptions.FILE_NAME));
        assertFalse(written.contains("weather_quality") || written.contains("multi_draw_mode") || written.contains("chunk_builder_threads"),
            "Celeritas's settings stay out of Demonica's file: " + written);

        // Once Demonica has its own file, Actinium's is not read again.
        Files.writeString(actinium, ACTINIUM.replace("\"loading_screen_framerate_limit\": 90", "\"loading_screen_framerate_limit\": 120"));
        assertEquals(90, DemonicaOptions.load(dir).performance.loadingScreenFramerateLimit);
    }

    @Test
    void startsFromDefaultsWithoutAnyFile(@TempDir Path dir) {
        DemonicaOptions options = DemonicaOptions.load(dir);

        assertFalse(options.enableDebugTab);
        assertFalse(options.performance.useFastBlockRenderer);
        assertEquals(60, options.performance.loadingScreenFramerateLimit);
        assertEquals(DemonicaOptions.StreamingUploadStrategy.MAP_BUFFER_RANGE, options.advanced.streamingUploadStrategy);
        assertNull(options.window.fullscreenMode);
        assertTrue(Files.exists(dir.resolve(DemonicaOptions.FILE_NAME)));
    }

    @Test
    void savedChangesAreReadBack(@TempDir Path dir) {
        DemonicaOptions options = DemonicaOptions.load(dir);
        options.performance.useFastBlockRenderer = true;
        options.advanced.streamingUploadStrategy = DemonicaOptions.StreamingUploadStrategy.BUFFER_SUB_DATA;
        options.save();

        DemonicaOptions reloaded = DemonicaOptions.load(dir);

        assertTrue(reloaded.performance.useFastBlockRenderer);
        assertEquals(DemonicaOptions.StreamingUploadStrategy.BUFFER_SUB_DATA, reloaded.advanced.streamingUploadStrategy);
    }

    @Test
    void anUnreadableFileIsKeptForTheUserToFix(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve(DemonicaOptions.FILE_NAME), "{ not json", StandardCharsets.UTF_8);

        DemonicaOptions options = DemonicaOptions.load(dir);

        assertFalse(options.performance.useFastBlockRenderer);
        assertEquals("{ not json", Files.readString(file));
    }
}
