package com.demonica.celeritas.guard;

import com.demonica.celeritas.CeleritasJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version gate (docs/celeritas/LEDGER.md, "The guard"): a pinned SHA-256 applies every patch, anything else turns
 * shaders off and applies only the BASE group, and the pin the build writes is the one gradle.properties names.
 */
class QuarantineGuardTest {
    private static final String PINNED = "1579bd31efdcc8832b29540173a455fcfb0ad2566c2f10b2f01fc20b679e8c92";
    private static final String DEV = "0c2b9b77a756af2b2f4a8bec25a1e8e4d483cd9dedd180d5168ebe1c10dbb423";
    private static final String FOREIGN = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeeeffffffff0000000011111111";
    private static final QuarantineGuard.Pin PIN = new QuarantineGuard.Pin("06999aabc2ea41a772ea3d0888c88a9e72d09f02",
        "2.4.0-autobuild.06999aab", Set.of(PINNED), DEV);
    private static final String TERRAIN_MIXIN = "com.demonica.mixin.celeritas.seam.CeleritasWorldRendererMixin";
    private static final String FOG_MIXIN = "com.demonica.mixin.celeritas.seam.GLStateManagerFogServiceMixin";
    private static final QuarantineGuard.GroupLookup GROUPS =
        mixin -> mixin.equals(FOG_MIXIN) ? PatchGroup.BASE : PatchGroup.CORE_TERRAIN;

    @Test
    void aPinnedJarAppliesEveryPatch() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, "celeritas.jar", PINNED.toUpperCase(Locale.ROOT), false);
        assertTrue(verdict.accepted());
        assertTrue(verdict.shadersAllowed());
        assertNull(verdict.shaderReason());
        assertTrue(verdict.shouldApply(TERRAIN_MIXIN, GROUPS));
        assertTrue(verdict.shouldApply(FOG_MIXIN, GROUPS));
    }

    @Test
    void anotherJarGetsOnlyTheFogPatchAndNoShaders() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, "celeritas-newer.jar", FOREIGN, false);
        assertFalse(verdict.accepted());
        assertFalse(verdict.shadersAllowed());
        assertNotNull(verdict.shaderReason());
        assertTrue(verdict.shaderReason().contains("celeritas-newer.jar"), verdict.shaderReason());
        assertTrue(verdict.shaderReason().contains("06999aab"), verdict.shaderReason());
        assertFalse(verdict.shouldApply(TERRAIN_MIXIN, GROUPS));
        assertTrue(verdict.shouldApply(FOG_MIXIN, GROUPS));
        assertFalse(verdict.shouldApply(FOG_MIXIN, mixin -> null), "a mixin whose group cannot be read must not apply");
    }

    @Test
    void aJarThatCannotBeFoundOrReadIsRejected() {
        QuarantineGuard.Verdict missing = QuarantineGuard.decide(PIN, null, null, null, true);
        assertFalse(missing.accepted());
        assertTrue(missing.shaderReason().contains("could not find the Celeritas jar"), missing.shaderReason());

        QuarantineGuard.Verdict unreadable = QuarantineGuard.decide(PIN, null, "celeritas.jar", null, true);
        assertFalse(unreadable.accepted());
        assertTrue(unreadable.shaderReason().contains("could not read celeritas.jar"), unreadable.shaderReason());
    }

    @Test
    void withoutThePinNothingButTheFogPatchApplies() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(null, QuarantineGuard.RESOURCE + " is missing from the Demonica jar",
            "celeritas.jar", PINNED, true);
        assertFalse(verdict.accepted());
        assertTrue(verdict.shaderReason().contains(QuarantineGuard.RESOURCE), verdict.shaderReason());
        assertTrue(verdict.shouldApply(FOG_MIXIN, GROUPS));
        assertFalse(verdict.shouldApply(TERRAIN_MIXIN, GROUPS));
    }

    @Test
    void theWorkspaceRemapCountsOnlyInDevelopment() {
        assertTrue(QuarantineGuard.decide(PIN, null, "celeritas.jar", DEV, true).accepted());
        assertFalse(QuarantineGuard.decide(PIN, null, "celeritas.jar", DEV, false).accepted());
        QuarantineGuard.Pin withoutDev = new QuarantineGuard.Pin(PIN.upstreamCommit(), PIN.version(), PIN.accepted(), null);
        assertFalse(QuarantineGuard.decide(withoutDev, null, "celeritas.jar", DEV, true).accepted());
    }

    @Test
    void aPinWithoutItsKeysIsUnreadable() {
        assertThrows(IOException.class, () -> QuarantineGuard.Pin.read(stream("")));
        assertThrows(IOException.class, () -> QuarantineGuard.Pin.read(stream("celeritas_sha=abc\nceleritas_version=1\n")));
        assertThrows(IOException.class, () -> QuarantineGuard.Pin.read(stream("celeritas_sha=abc\nceleritas_version=1\nceleritas_sha256= , \n")));
    }

    @Test
    void aPinIsReadWithItsHashesLowerCased() throws IOException {
        QuarantineGuard.Pin pin = QuarantineGuard.Pin.read(stream("celeritas_sha=abc\nceleritas_version=1\nceleritas_sha256=AB, cd\n"));
        assertEquals(Set.of("ab", "cd"), pin.accepted());
        assertNull(pin.devSha256());
    }

    @Test
    void hashesAFile(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("abc"), "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", QuarantineGuard.sha256(file.toFile()));
    }

    /**
     * The pin the build wrote names the SHA-256s of gradle.properties, and its dev hash is the Celeritas jar the tests
     * read: the dev client, which runs that same remap, is accepted.
     */
    @Test
    void theGeneratedPinMatchesTheBuild() throws IOException {
        QuarantineGuard.Pin pin;
        try (InputStream in = QuarantineGuardTest.class.getClassLoader().getResourceAsStream(QuarantineGuard.RESOURCE)) {
            assertNotNull(in, QuarantineGuard.RESOURCE + " is not on the test classpath");
            pin = QuarantineGuard.Pin.read(in);
        }
        Properties gradle = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("demonica.projectRoot", "."), "gradle.properties"))) {
            gradle.load(reader);
        }
        Set<String> pins = Arrays.stream(gradle.getProperty("celeritas_sha256").split(",")).map(String::trim).collect(Collectors.toSet());
        assertEquals(pins, pin.accepted());
        assertEquals(gradle.getProperty("celeritas_sha").trim(), pin.upstreamCommit());
        assertEquals(gradle.getProperty("celeritas_version").trim(), pin.version());
        assertEquals(QuarantineGuard.sha256(CeleritasJar.get().file()), pin.devSha256());
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
