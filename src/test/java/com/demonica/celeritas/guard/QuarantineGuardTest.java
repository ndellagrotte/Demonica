package com.demonica.celeritas.guard;

import com.demonica.celeritas.CeleritasJar;
import com.demonica.loading.bundle.BundledCeleritasHook;
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
import java.util.List;
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
    private static final String PINNED = "5bf123a6da6190f6e061cd3fd069a2bddcbf6ebf57894381b6c888c06a94f982";
    private static final String DEV = "0c2b9b77a756af2b2f4a8bec25a1e8e4d483cd9dedd180d5168ebe1c10dbb423";
    private static final String FOREIGN = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeeeffffffff0000000011111111";
    private static final String BUNDLED = "zz-demonica-bundled-celeritas-9b661b70-20260926T020851.jar";
    private static final String STALE = "zz-demonica-bundled-celeritas-06999aab-20260901T000000.jar";
    private static final QuarantineGuard.Pin PIN = new QuarantineGuard.Pin("9b661b70ecaf84c39052e8fbc51c875ba37e739d",
        "2.5.0-autobuild.9b661b70", Set.of(PINNED), DEV);
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
        assertTrue(verdict.shaderReason().contains("9b661b70"), verdict.shaderReason());
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

    /** An installed Celeritas takes precedence over the bundled copy; a build other than the pin is named, with both ways out. */
    @Test
    void anInstalledJarThatTakesPrecedenceIsNamedWithTheWaysOut() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, "celeritas-newer.jar", FOREIGN, false, BUNDLED,
            QuarantineGuard.Bundle.INSTALLED_TAKES_PRECEDENCE);
        assertFalse(verdict.accepted());
        assertTrue(verdict.shaderReason().contains("celeritas-newer.jar is installed in the mods folder and takes precedence over "
            + "the Celeritas bundled with Demonica; remove it to use the bundled build of upstream commit 9b661b70, or install "
            + "that build."), verdict.shaderReason());
        assertFalse(verdict.shaderReason().contains("was not extracted"), verdict.shaderReason());

        assertTrue(QuarantineGuard.decide(PIN, null, "celeritas-pinned.jar", PINNED, false, BUNDLED,
            QuarantineGuard.Bundle.INSTALLED_TAKES_PRECEDENCE).accepted(), "an installed copy of the pinned build is accepted");
    }

    /** A copy an earlier Demonica extracted can win the class path when the hook did not run; it is already deleted. */
    @Test
    void aJarLoadedInsteadOfTheBundledCopyInUseIsNamed() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, STALE, FOREIGN, false, BUNDLED,
            QuarantineGuard.Bundle.IN_USE);
        assertFalse(verdict.accepted());
        assertTrue(verdict.shaderReason().contains(STALE + " was loaded instead of the Celeritas bundled with Demonica"),
            verdict.shaderReason());
        assertTrue(verdict.shaderReason().contains("restart the game"), verdict.shaderReason());
        assertFalse(verdict.shaderReason().contains("Install"), "nothing is to be installed: " + verdict.shaderReason());
    }

    /** With the bundled copy skipped or not extracted, the jar that loaded may be the only Celeritas the player has. */
    @Test
    void withoutTheBundledCopyTheLoadedJarIsNotNamedForRemoval() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, "celeritas-newer.jar", FOREIGN, false, BUNDLED,
            QuarantineGuard.Bundle.NOT_EXTRACTED);
        assertFalse(verdict.accepted());
        assertFalse(verdict.shaderReason().contains("remove it"), verdict.shaderReason());
        assertFalse(verdict.shaderReason().contains("takes precedence"), verdict.shaderReason());
        assertTrue(verdict.shaderReason().contains("mods/1.12.2/" + BUNDLED + ") was not extracted"), verdict.shaderReason());
        assertTrue(verdict.shaderReason().contains("-Dfml.skipContainedDeps"), verdict.shaderReason());
        assertTrue(verdict.shaderReason().contains("9b661b70"), verdict.shaderReason());

        QuarantineGuard.Verdict damaged = QuarantineGuard.decide(PIN, null, BUNDLED, FOREIGN, false, BUNDLED,
            QuarantineGuard.Bundle.NOT_EXTRACTED);
        assertTrue(damaged.shaderReason().contains("damaged"), "a damaged bundled copy is still named: " + damaged.shaderReason());
    }

    /** The decision's bundledPresent tells an installed jar that takes precedence from the player's only Celeritas. */
    @Test
    void theBundledCopysPlaceComesFromTheDecision() {
        assertEquals(QuarantineGuard.Bundle.IN_USE, QuarantineGuard.bundle(null));
        assertEquals(QuarantineGuard.Bundle.IN_USE, QuarantineGuard.bundle(decision(true, true, List.of())));
        assertEquals(QuarantineGuard.Bundle.INSTALLED_TAKES_PRECEDENCE,
            QuarantineGuard.bundle(decision(false, true, List.of("celeritas-newer.jar"))));
        assertEquals(QuarantineGuard.Bundle.NOT_EXTRACTED,
            QuarantineGuard.bundle(decision(false, false, List.of("celeritas-newer.jar"))),
            "skipped by -Dfml.skipContainedDeps: removing the installed jar would leave no Celeritas");
        assertEquals(QuarantineGuard.Bundle.NOT_EXTRACTED, QuarantineGuard.bundle(decision(false, false, List.of())));
    }

    /** The report says an installed jar is used; it is repeated only when that jar is the one that loaded. */
    @Test
    void theReportIsRepeatedOnlyForTheInstalledJarThatLoaded() {
        BundledCeleritasHook.Decision installed = decision(false, true, List.of("zcel.jar"));
        assertTrue(QuarantineGuard.reportsTheLoadedJar(installed, "zcel.jar"));
        assertFalse(QuarantineGuard.reportsTheLoadedJar(installed, BUNDLED),
            "the hook did not run and the bundled copy won the class path");
        assertFalse(QuarantineGuard.reportsTheLoadedJar(installed, null));
        assertFalse(QuarantineGuard.reportsTheLoadedJar(decision(true, true, List.of()), BUNDLED));
        assertFalse(QuarantineGuard.reportsTheLoadedJar(null, "zcel.jar"));
    }

    @Test
    void aDamagedBundledCopyIsToBeExtractedAgain() {
        for (QuarantineGuard.Bundle bundle : QuarantineGuard.Bundle.values()) {
            // INSTALLED_TAKES_PRECEDENCE too: when the hook did not run, the bundled copy can still win the class path.
            QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, BUNDLED, FOREIGN, false, BUNDLED, bundle);
            assertFalse(verdict.accepted());
            assertTrue(verdict.shaderReason().contains("damaged"), bundle + ": " + verdict.shaderReason());
            assertTrue(verdict.shaderReason().contains("mods/1.12.2/" + BUNDLED), bundle + ": " + verdict.shaderReason());
        }

        assertTrue(QuarantineGuard.decide(PIN, null, BUNDLED, PINNED, false, BUNDLED, QuarantineGuard.Bundle.IN_USE).accepted(),
            "the pinned bundled copy is accepted");
    }

    @Test
    void withoutAnyJarTheBundledCopyIsToBeExtractedAgain() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(PIN, null, null, null, false, BUNDLED, QuarantineGuard.Bundle.IN_USE);
        assertFalse(verdict.accepted());
        assertTrue(verdict.shaderReason().contains("Delete mods/1.12.2/" + BUNDLED), verdict.shaderReason());
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

    private static BundledCeleritasHook.Decision decision(boolean useBundled, boolean bundledPresent, List<String> externals) {
        return new BundledCeleritasHook.Decision(BUNDLED, useBundled, bundledPresent, externals, List.of(), "report");
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
