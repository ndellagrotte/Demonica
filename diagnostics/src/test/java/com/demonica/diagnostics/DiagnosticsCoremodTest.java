package com.demonica.diagnostics;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiagnosticsCoremodTest {
    @Test
    void jarPathReadsFileAndJarUrls() {
        assertEquals(Path.of("/mods/Demonica-0.4.0.jar"), DiagnosticsCoremod.jarPath("file:/mods/Demonica-0.4.0.jar"));
        assertEquals(Path.of("/mods/Demonica-0.4.0.jar"),
            DiagnosticsCoremod.jarPath("jar:file:/mods/Demonica-0.4.0.jar!/com/demonica/mixins/MixinEarly.class"));
    }

    @Test
    void jarPathIgnoresClassDirectoriesAndClassFiles() {
        assertNull(DiagnosticsCoremod.jarPath("file:/work/build/classes/java/main/"));
        assertNull(DiagnosticsCoremod.jarPath("file:/work/build/classes/java/main/com/demonica/mixins/MixinEarly.class"));
    }
}
