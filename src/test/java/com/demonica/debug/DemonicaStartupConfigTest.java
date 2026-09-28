package com.demonica.debug;

import com.demonica.config.OpenGlProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemonicaStartupConfigTest {
    @Test
    void resolvesLwjglDebugFromPropertyOverrideOrConfiguredFallback() {
        assertTrue(DemonicaStartupConfig.resolveLwjglDebug("true", false));
        assertTrue(DemonicaStartupConfig.resolveLwjglDebug("TRUE", false));
        assertFalse(DemonicaStartupConfig.resolveLwjglDebug("false", true));
        assertTrue(DemonicaStartupConfig.resolveLwjglDebug(null, true));
        assertFalse(DemonicaStartupConfig.resolveLwjglDebug(null, false));
    }

    @Test
    void resolvesTheOpenGlProfileFromPropertyOverrideOrConfiguredFallback() {
        assertEquals(OpenGlProfile.CORE, DemonicaStartupConfig.resolveOpenGlProfile("core", "AUTO"));
        assertEquals(OpenGlProfile.COMPATIBILITY, DemonicaStartupConfig.resolveOpenGlProfile(null, "compatibility"));
        assertEquals(OpenGlProfile.AUTO, DemonicaStartupConfig.resolveOpenGlProfile(null, null));
        assertEquals(OpenGlProfile.AUTO, DemonicaStartupConfig.resolveOpenGlProfile("bogus", "core"),
            "an unknown override is auto, not the file's value");
    }

    @Test
    void readsEachSectionOnItsOwn(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("demonica-options.json");

        Files.writeString(file, "{ \"advanced\": { \"opengl_profile\": \"CORE\" } }");
        DemonicaStartupConfig.Snapshot advancedOnly = DemonicaStartupConfig.readSnapshot(file);
        assertEquals("CORE", advancedOnly.openGlProfile());
        assertFalse(advancedOnly.enableLwjglDebug());

        Files.writeString(file, "{ \"debug\": { \"enable_lwjgl_debug\": true } }");
        DemonicaStartupConfig.Snapshot debugOnly = DemonicaStartupConfig.readSnapshot(file);
        assertNull(debugOnly.openGlProfile());
        assertTrue(debugOnly.enableLwjglDebug());

        assertEquals(DemonicaStartupConfig.Snapshot.DEFAULT, DemonicaStartupConfig.readSnapshot(dir.resolve("missing.json")));
    }
}
