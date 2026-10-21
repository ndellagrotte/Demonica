package com.demonica.loading.bundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The hook that decides which Celeritas the game loads when Demonica carries one (docs/celeritas/PIN.md, "Bundled
 * copy"): an installed Celeritas takes precedence, and the bundled copy, extracted to its folder in mods/memory_repo,
 * is then left out (and left on disk, inert); what it leaves out of Cleanroom's list of candidates, what it deletes
 * (copies in mods/1.12.2 from Demonica 0.8.2 and earlier, older pins' folders) and never deletes, the fallback and the
 * reconciliation of FML's lists in {@code MixinEarly}, and the constraints that come from running before any coremod.
 */
class BundledCeleritasHookTest {
    private static final String BUNDLED = "zz-demonica-bundled-celeritas-d94ec1e5-20261014T021746.jar";
    private static final String BUNDLED_VERSION = "d94ec1e5-20261014T021746";
    private static final String STALE = "zz-demonica-bundled-celeritas-06999aab-20260901T000000.jar";
    private static final String OTHER_DEMONICAS = "zz-demonica-bundled-celeritas-12345678-20261001T000000.jar";
    private static final String INSTALLED = "01-celeritas-forge-mc12.2-2.5.0-dev.jar";
    private static final String DEMONICA = "Demonica-0.8.0.jar";
    private static final String CELERITAS_CLASS = BundledCeleritasHook.CELERITAS_MARKER;
    private static final String RELEASE = "commit-d94ec1e59df7b5fe38ee76f1bdcb03f851587aa4-20261014T021746";
    private static final String REPOSITORY = "mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas";

    @TempDir
    Path home;
    private Path mods;
    private Path versionMods;
    /** The bundled copy's version folder in the repository, where Cleanroom extracts it. */
    private Path repository;

    @BeforeEach
    void layOut() throws IOException {
        BundledCeleritasHook.resetForTests();
        mods = Files.createDirectories(home.resolve("mods"));
        versionMods = Files.createDirectories(mods.resolve("1.12.2"));
        repository = Files.createDirectories(home.resolve(REPOSITORY).resolve(BUNDLED_VERSION));
    }

    @AfterEach
    void forget() {
        BundledCeleritasHook.resetForTests();
    }

    @Test
    void recognisesCeleritasByItsModIdOrItsCoremod() throws IOException {
        assertTrue(BundledCeleritasHook.isCeleritasJar(celeritas(mods.resolve("celeritas.jar"))));
        assertTrue(BundledCeleritasHook.isCeleritasJar(celeritasCoremodOnly(mods.resolve("celeritas-coremod.jar"))));
        assertFalse(BundledCeleritasHook.isCeleritasJar(fat(mods.resolve("fat.jar"))), "shading Celeritas's classes does not make a mod");
        assertFalse(BundledCeleritasHook.isCeleritasJar(plainMod(mods.resolve("plain.jar"))));
        assertFalse(BundledCeleritasHook.isCeleritasJar(corrupt(mods.resolve("corrupt.jar"))));
        assertFalse(BundledCeleritasHook.isCeleritasJar(Files.createDirectories(mods.resolve("exploded.jar")).toFile()));
        assertFalse(BundledCeleritasHook.isCeleritasJar(mods.resolve("missing.jar").toFile()));
    }

    @Test
    void planWithAnInstalledCeleritasGivesUpTheBundledCopy() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File renamed = celeritas(mods.resolve("my-renderer.jar"));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        List<File> candidates = candidates(sorted(installed, renamed, stale, demonica(mods.resolve(DEMONICA), BUNDLED),
            plainMod(mods.resolve("plain.jar"))), bundled);

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(bundled, plan.bundled());
        assertEquals(List.of(installed, renamed), plan.externals(), "every installed Celeritas, in the candidates' order");
        assertEquals(List.of(stale), plan.dropStale(), "another Celeritas is left, so the stale copy goes");
        assertFalse(plan.useBundled());
        assertTrue(plan.dropBundled());
    }

    @Test
    void planWithoutAnInstalledCeleritasUsesTheBundledCopy() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        List<File> candidates = candidates(sorted(stale, demonica(mods.resolve(DEMONICA), BUNDLED), plainMod(mods.resolve("plain.jar"))),
            bundled);

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(bundled, plan.bundled());
        assertEquals(List.of(), plan.externals());
        assertEquals(List.of(stale), plan.dropStale());
        assertTrue(plan.useBundled());
        assertFalse(plan.dropBundled());
    }

    @Test
    void planOnADedicatedServerLeavesOutTheBundledCopy() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        List<File> candidates = candidates(sorted(installed, stale), bundled);

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, false, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(List.of(installed), plan.externals());
        assertEquals(List.of(stale), plan.dropStale(), "a dedicated server still cleans up after earlier pins");
        assertFalse(plan.useBundled());
        assertTrue(plan.dropBundled());
    }

    @Test
    void planWithoutTheBundledCopyDropsAStaleCopyOnlyWhileAnotherCeleritasIsLeft() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File plain = plainMod(mods.resolve("plain.jar"));

        BundledCeleritasHook.Plan alone = BundledCeleritasHook.plan(sorted(stale, plain), BUNDLED, true,
            BundledCeleritasHook::isCeleritasJar, Set.of(BUNDLED));
        assertEquals(List.of(), alone.dropStale(), "a stale copy may be the only Celeritas left");
        assertEquals(List.of(), alone.externals());
        assertNull(alone.bundled());
        assertFalse(alone.useBundled() || alone.dropBundled());

        File installed = celeritas(mods.resolve(INSTALLED));
        BundledCeleritasHook.Plan withInstalled = BundledCeleritasHook.plan(sorted(installed, stale, plain), BUNDLED, true,
            BundledCeleritasHook::isCeleritasJar, Set.of(BUNDLED));
        assertEquals(List.of(stale), withInstalled.dropStale(), "the installed Celeritas is left");
        assertEquals(List.of(installed), withInstalled.externals());
        assertFalse(withInstalled.useBundled() || withInstalled.dropBundled());
    }

    /**
     * Demonica 0.8.2 and earlier extracted the bundled copy to mods/1.12.2, where it loads as a standalone Celeritas
     * once Demonica's jar is disabled: on the first launch after the update that copy is still a candidate, next to the
     * one Cleanroom extracted to the repository. It is a leftover, deleted, and the repository copy is the one used.
     */
    @Test
    void aLeftoverOfTheOldExtractionIsStaleAndDeleted() throws IOException {
        File leftover = celeritas(versionMods.resolve(BUNDLED));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(leftover, demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(List.of(demonica, bundled), candidates);
        assertTrue(bundled.exists());
        assertFalse(leftover.exists(), "the copy in mods/1.12.2 would load without Demonica's jar");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertTrue(decision.useBundled());
        assertEquals(bundled, decision.bundledFile());
        assertEquals(List.of(BUNDLED), decision.droppedStale(), "the leftover is told apart by its folder");
        assertTrue(decision.report().contains("Removed 1 copy of Celeritas an earlier Demonica extracted to mods/1.12.2: " + BUNDLED),
            decision.report());
    }

    /** Without the repository copy (extraction off), a copy in mods/1.12.2 is still the bundled copy, and kept. */
    @Test
    void withoutTheRepositoryCopyALeftoverIsTheBundledCopy() throws IOException {
        File leftover = celeritas(versionMods.resolve(BUNDLED));
        File copied = celeritas(mods.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = sorted(leftover, copied, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(List.of(demonica, leftover), candidates, "the copy in mods/1.12.2 is preferred to the player's copy");
        assertTrue(leftover.exists() && copied.exists());
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertTrue(decision.useBundled());
        assertEquals(leftover, decision.bundledFile());
        assertEquals(List.of(), decision.droppedStale());
    }

    /** Older pins' version folders in the repository: never candidates, but deleted so that they do not pile up. */
    @Test
    void olderPinsInTheRepositoryAreDeleted() throws IOException {
        Path older = Files.createDirectories(repository.resolveSibling("06999aab-20260901T000000"));
        File olderJar = celeritas(older.resolve(STALE));
        Files.writeString(older.resolve(STALE + ".meta"), "Manifest-Version: 1.0\n");
        Path unrelated = Files.createDirectories(home.resolve("mods/memory_repo/com/example/lib/1.0"));
        File unrelatedJar = plainMod(unrelated.resolve("lib-1.0.jar"));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertFalse(Files.exists(older), "the older pin's folder, jar and sidecar are gone");
        assertFalse(olderJar.exists());
        assertTrue(bundled.exists() && unrelatedJar.exists(), "the bundled copy and other artifacts are left alone");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertTrue(decision.useBundled());
        assertEquals(List.of(), decision.droppedStale(), "a folder nothing lists is not a dropped candidate");
        assertTrue(decision.report().endsWith("Removed 1 older pin from " + REPOSITORY + ": 06999aab-20260901T000000."),
            decision.report());

        BundledCeleritasHook.resetForTests();
        Files.createDirectories(older);
        celeritas(older.resolve(STALE));
        BundledCeleritasHook.Decision fallback = BundledCeleritasHook.decideWithoutHook(List.of(bundled), BUNDLED, true);
        assertTrue(fallback.useBundled());
        assertFalse(Files.exists(older), "the fallback cleans up too");
    }

    @Test
    void theExtractedPathFollowsTheArtifact() {
        assertEquals(REPOSITORY + "/" + BUNDLED_VERSION + "/" + BUNDLED, BundledCeleritasHook.extractedPath(BUNDLED));
        assertEquals(BUNDLED_VERSION, BundledCeleritasHook.versionOf(BUNDLED));
        assertEquals(home.resolve(REPOSITORY).toFile(), BundledCeleritasHook.bundledArtifactFolder(home.toFile()));
        assertNull(BundledCeleritasHook.bundledVersionFolder(home.toFile()), "the test classes are not a bundled jar");
        assertTrue(BundledCeleritasHook.inBundledRepository(repository.resolve(BUNDLED).toFile()));
        assertFalse(BundledCeleritasHook.inBundledRepository(versionMods.resolve(BUNDLED).toFile()));
        assertFalse(BundledCeleritasHook.inBundledRepository(home.resolve("mods/memory_repo/com/example/lib/1.0/lib-1.0.jar").toFile()));
        assertEquals(BundledCeleritasHook.BUNDLED_ARTIFACT + "-", BundledCeleritasHook.BUNDLED_PREFIX);
    }

    @Test
    void withoutTheBundledCopyTheHookKeepsAStaleCopy() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = sorted(stale, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(sorted(stale, demonica), candidates);
        assertTrue(stale.exists(), "the only Celeritas on disk is never deleted");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertFalse(decision.useBundled());
        assertEquals(List.of(), decision.externals());
        assertEquals(List.of(), decision.droppedStale());
        assertTrue(decision.report().contains("is not in " + REPOSITORY), decision.report());
    }

    /** Only Demonica 0.8.2 and earlier extracted to mods/1.12.2, so a prefixed copy in mods was put there by the player: it is installed. */
    @Test
    void aPrefixedCopyOutsideTheExtractionFolderCountsAsInstalled() throws IOException {
        File placed = celeritas(mods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(placed, demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(sorted(placed, demonica), candidates);
        assertTrue(placed.exists(), "a jar the player put in the mods folder is never touched");
        assertTrue(bundled.exists(), "the bundled copy stays, inert, for when the installed jar is removed");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertFalse(decision.useBundled());
        assertEquals(List.of(STALE), decision.externals());
        assertEquals(List.of(), decision.droppedStale());
        assertTrue(decision.report().contains("Using the installed mods/" + STALE + " instead of the Celeritas bundled with "
            + "Demonica; remove it from the mods folder to use the bundled build."), decision.report());
    }

    @Test
    void aCopyAnotherInstalledDemonicaCarriesIsNotStale() throws IOException {
        File others = celeritas(versionMods.resolve(OTHER_DEMONICAS));
        File bundled = celeritas(repository.resolve(BUNDLED));
        List<File> candidates = candidates(sorted(others), bundled);

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED, OTHER_DEMONICAS));

        assertEquals(List.of(), plan.dropStale(), "never deleted while a jar in the mods folder names it");
        assertEquals(List.of(others), plan.externals(), "an installed Celeritas like any other");
        assertFalse(plan.useBundled());
        assertTrue(plan.dropBundled());
    }

    @Test
    void keepsWhatIsNotACeleritasMod() throws IOException {
        List<File> candidates = candidates(sorted(plainMod(versionMods.resolve(STALE)),
            fat(mods.resolve("fat.jar")), corrupt(mods.resolve("corrupt.jar")),
            Files.createDirectories(mods.resolve("exploded")).toFile()), celeritas(repository.resolve(BUNDLED)));

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(List.of(), plan.externals(), "a jar that only shades Celeritas is not an installed Celeritas");
        assertEquals(List.of(), plan.dropStale(), "a prefixed jar that is not Celeritas is not Demonica's");
        assertTrue(plan.useBundled());
    }

    @Test
    void theHookGivesWayToAnInstalledCeleritasAndLeavesTheBundledCopyOnDisk() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED, "jcpp-1.4.14.jar");
        File plain = plainMod(mods.resolve("plain.jar"));
        List<File> candidates = candidates(sorted(installed, stale, demonica, plain), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, true);

        assertEquals(sorted(installed, demonica, plain), candidates, "the installed jar stays on the list");
        assertTrue(installed.exists(), "the jar the player installed is never touched");
        assertTrue(bundled.exists(), "the bundled copy stays, inert: nothing lists its folder, and it is used again once the "
            + "installed jar is gone");
        assertFalse(stale.exists(), "a copy an earlier Demonica extracted is deleted");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertSame(decision, BundledCeleritasHook.decisionOrFallback());
        assertEquals(BUNDLED, decision.bundledName());
        assertFalse(decision.useBundled());
        assertTrue(decision.bundledPresent());
        assertEquals(bundled, decision.bundledFile());
        assertEquals(List.of(INSTALLED), decision.externals());
        assertEquals(List.of(STALE), decision.droppedStale());
        assertTrue(decision.report().startsWith("Using the installed mods/" + INSTALLED + " instead of the Celeritas bundled "
            + "with Demonica; remove it from the mods folder to use the bundled build."), decision.report());
        assertTrue(decision.report().contains(STALE), decision.report());
        assertEquals(BUNDLED, BundledCeleritasHook.bundledJarName());
    }

    /**
     * With the bundled copy skipped (-Dfml.skipContainedDeps) or not extracted, the installed jar is the player's only
     * Celeritas: the report must not tell them to remove it.
     */
    @Test
    void withoutTheBundledCopyTheInstalledJarIsNotNamedForRemoval() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = sorted(installed, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(sorted(installed, demonica), candidates);
        assertTrue(installed.exists());
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertFalse(decision.bundledPresent());
        assertFalse(decision.useBundled());
        assertEquals(List.of(INSTALLED), decision.externals());
        assertFalse(decision.report().contains("to use the bundled build"), decision.report());
        assertEquals("Using the installed mods/" + INSTALLED + "; the Celeritas bundled with Demonica, " + BUNDLED
            + " (Celeritas-auto-build " + RELEASE + "), is not in " + REPOSITORY + " (it was not extracted, or -Dfml.skipContainedDeps "
            + "names it).", decision.report());
    }

    /**
     * A copy of the bundled jar in mods is the bundled copy, not an installed Celeritas: otherwise the advice to remove
     * the "installed" jar would never resolve. Demonica's own extraction is the one kept.
     */
    @Test
    void aCopyOfTheBundledJarElsewhereIsTheBundledCopy() throws IOException {
        File copied = celeritas(mods.resolve(BUNDLED));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(copied, demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(List.of(demonica, bundled), candidates, "Demonica's own extraction is kept, the copy left out");
        assertTrue(copied.exists() && bundled.exists(), "nothing is deleted");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertTrue(decision.useBundled());
        assertTrue(decision.bundledPresent());
        assertEquals(bundled, decision.bundledFile());
        assertEquals(List.of(), decision.externals());
        assertEquals(List.of(), decision.droppedStale());
    }

    @Test
    void theHookUsesTheBundledCopyWhenNothingIsInstalled() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED, "jcpp-1.4.14.jar");
        File plain = plainMod(mods.resolve("plain.jar"));
        List<File> candidates = candidates(sorted(stale, demonica, plain), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, true);

        assertEquals(candidates(sorted(demonica, plain), bundled), candidates);
        assertTrue(bundled.exists());
        assertFalse(stale.exists());
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertTrue(decision.useBundled());
        assertEquals(bundled, decision.bundledFile());
        assertEquals(List.of(), decision.externals());
        assertEquals(List.of(STALE), decision.droppedStale());
        assertTrue(decision.report().startsWith("Using the Celeritas bundled with Demonica, " + BUNDLED + " (Celeritas-auto-build "
            + RELEASE + ")."), decision.report());
    }

    /**
     * The hook's destructive actions, end to end: the names it must not delete come from the manifests it reads, so a
     * second jar's ContainedDeps (space-separated, more than one entry) protects the copy it carries, which is then an
     * installed Celeritas that takes precedence.
     */
    @Test
    void theHookNeverDeletesACopyAnotherInstalledJarCarries() throws IOException {
        File others = celeritas(versionMods.resolve(OTHER_DEMONICAS));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File own = demonica(mods.resolve(DEMONICA), BUNDLED);
        File second = containing(mods.resolve("demonica-0.7.9-fork.jar"), "jcpp-1.4.14.jar " + OTHER_DEMONICAS);
        List<File> candidates = candidates(sorted(others, stale, own, second), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertTrue(others.exists(), "another installed jar names it");
        assertTrue(candidates.contains(others), "it is installed, and takes precedence");
        assertFalse(stale.exists(), "no jar names it");
        assertTrue(bundled.exists());
        assertFalse(candidates.contains(bundled));
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertEquals(List.of(OTHER_DEMONICAS), decision.externals());
        assertEquals(List.of(STALE), decision.droppedStale());
    }

    /** The fallback in MixinEarly, when the hook never ran: the same rules, with the mods folders read from disk. */
    @Test
    void withoutTheHookAnInstalledCeleritasTakesPrecedence() throws IOException {
        File others = celeritas(versionMods.resolve(OTHER_DEMONICAS));
        File stale = celeritas(versionMods.resolve(STALE));
        File placed = celeritas(mods.resolve("zz-demonica-bundled-celeritas-00000000-20250101T000000.jar"));
        File installed = celeritas(mods.resolve(INSTALLED));
        File bundled = celeritas(repository.resolve(BUNDLED));
        demonica(mods.resolve(DEMONICA), BUNDLED);
        containing(mods.resolve("demonica-0.7.9-fork.jar"), "jcpp-1.4.14.jar " + OTHER_DEMONICAS);

        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decideWithoutHook(
            List.of(placed, installed, others, stale, bundled), BUNDLED, true);

        assertFalse(decision.useBundled());
        assertEquals(List.of(placed.getName(), INSTALLED, OTHER_DEMONICAS), decision.externals());
        assertEquals(List.of(STALE), decision.droppedStale());
        assertFalse(stale.exists());
        assertTrue(others.exists() && placed.exists() && installed.exists());
        assertTrue(bundled.exists(), "never deleted");
        assertEquals(bundled, decision.bundledFile());
        assertNull(BundledCeleritasHook.decision(), "the hook did not run");
        assertSame(decision, BundledCeleritasHook.decisionOrFallback());
    }

    /**
     * Without the hook, the bundled copy in the repository is listed after every jar of the mods folders, so an installed
     * jar wins the class path whatever its name. A copy left in mods/1.12.2 by Demonica 0.8.2 or earlier is another
     * matter: NonModDeps adds it at Demonica's place, so an installed jar that sorts after Demonica's loses to it.
     * MixinEarly tells the two apart by the leftover among the stale copies, and says so instead of repeating the report.
     */
    @Test
    void withoutTheHookOnlyALeftoverInTheOldFolderCanBeatAnInstalledJar() throws IOException {
        File late = celeritas(mods.resolve("zcel.jar"));
        File bundled = celeritas(repository.resolve(BUNDLED));
        demonica(mods.resolve(DEMONICA), BUNDLED);

        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decideWithoutHook(List.of(late, bundled), BUNDLED, true);

        assertFalse(decision.useBundled());
        assertTrue(decision.bundledPresent());
        assertEquals(List.of("zcel.jar"), decision.externals());
        assertEquals(List.of(), decision.droppedStale(), "no leftover: the installed jar won, whatever its name");
        assertTrue(bundled.exists() && late.exists());

        BundledCeleritasHook.resetForTests();
        File leftover = celeritas(versionMods.resolve(BUNDLED));
        decision = BundledCeleritasHook.decideWithoutHook(List.of(late, leftover, bundled), BUNDLED, true);

        assertEquals(List.of(BUNDLED), decision.droppedStale(), "the leftover is what NonModDeps put on the class path");
        assertEquals(List.of(), BundledCeleritasHook.sortedBefore(decision.externals(), DEMONICA), "and it won");
        assertTrue(leftover.exists(), "the Celeritas classes load from it in this launch: deleted at exit, not now");
        assertTrue(bundled.exists());
    }

    @Test
    void withoutTheHookTheBundledCopyIsUsedWhenNothingIsInstalled() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        demonica(mods.resolve(DEMONICA), BUNDLED);

        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decideWithoutHook(List.of(stale, bundled), BUNDLED, true);

        assertTrue(decision.useBundled());
        assertEquals(bundled, decision.bundledFile());
        assertEquals(List.of(), decision.externals());
        assertEquals(List.of(STALE), decision.droppedStale());
        assertFalse(stale.exists());
        assertTrue(bundled.exists());
    }

    @Test
    void withoutTheHookAStaleCopyAloneIsKept() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));

        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decideWithoutHook(List.of(stale), BUNDLED, true);

        assertFalse(decision.useBundled());
        assertEquals(List.of(), decision.droppedStale());
        assertEquals(List.of(), decision.externals());
        assertTrue(stale.exists());
    }

    @Test
    void withoutTheHookADedicatedServerDoesNotUseTheBundledCopy() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File bundled = celeritas(repository.resolve(BUNDLED));

        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decideWithoutHook(List.of(installed, bundled), BUNDLED, false);

        assertFalse(decision.useBundled());
        assertEquals(List.of(INSTALLED), decision.externals());
        assertTrue(bundled.exists() && installed.exists());
    }

    /**
     * Without the hook, NonModDeps puts a copy left in mods/1.12.2 on the class path at the place of Demonica's own
     * jar: an installed jar wins the Celeritas classes only if its name sorts before Demonica's, case aside.
     */
    @Test
    void theFallbackTellsWhichJarsCameBeforeDemonicasOwn() {
        assertEquals(List.of(INSTALLED, "Celeritas.jar"),
            BundledCeleritasHook.sortedBefore(List.of(INSTALLED, "zz-renderer.jar", "Celeritas.jar", "demonica-addon.jar"), DEMONICA));
        assertEquals(List.of(), BundledCeleritasHook.sortedBefore(List.of(BUNDLED, STALE), DEMONICA),
            "the zz- prefix sorts the bundled copies after Demonica");
    }

    /** The bundled copy's own place in the loader's order comes after any Celeritas a player installs. */
    @Test
    void theBundledCopySortsAfterInstalledCeleritasJars() {
        for (String installed : List.of(INSTALLED, "celeritas-forge-mc12.2-2.5.0.jar", "Celeritas-2.6.0.jar", DEMONICA)) {
            assertTrue(installed.toLowerCase(Locale.ROOT).compareTo(BUNDLED.toLowerCase(Locale.ROOT)) < 0, installed);
        }
        assertTrue(BUNDLED.startsWith(BundledCeleritasHook.BUNDLED_PREFIX));
    }

    @Test
    void chainingPicksTheFirstOtherHookStillOnTheList() {
        File own = mods.resolve(DEMONICA).toFile();
        File second = mods.resolve("demonica-0.7.9.jar").toFile();
        File dropped = mods.resolve("a-dropped.jar").toFile();
        File first = mods.resolve("b-first.jar").toFile();
        File later = mods.resolve("c-later.jar").toFile();
        File noHook = mods.resolve("plain.jar").toFile();
        Map<File, Attributes> manifests = new LinkedHashMap<>();
        manifests.put(own, hookNamed(BundledCeleritasHook.class.getName()));
        manifests.put(second, hookNamed(" " + BundledCeleritasHook.class.getName() + " "));
        manifests.put(dropped, hookNamed("com.example.DroppedHook"));
        manifests.put(noHook, manifest().getMainAttributes());
        manifests.put(first, hookNamed("com.example.FirstHook"));
        manifests.put(later, hookNamed("com.example.LaterHook"));
        List<File> candidates = List.of(own, second, first, later, noHook);

        assertEquals(Map.entry(first, "com.example.FirstHook"), BundledCeleritasHook.chainTarget(candidates, manifests, own),
            "Demonica's own and a second Demonica's hook are skipped, and so is a jar no longer on the list");
        assertNull(BundledCeleritasHook.chainTarget(List.of(own, second, noHook), manifests, own));
    }

    @Test
    void chainingSkipsTheOwnJarWhateverItsHookSays() {
        File own = mods.resolve(DEMONICA).toFile();
        File other = mods.resolve("other.jar").toFile();
        Map<File, Attributes> manifests = new LinkedHashMap<>();
        manifests.put(own, hookNamed("com.example.Renamed"));
        manifests.put(other, hookNamed("com.example.OtherHook"));

        assertEquals(Map.entry(other, "com.example.OtherHook"), BundledCeleritasHook.chainTarget(List.of(own, other), manifests, own));
    }

    @Test
    void theStaleCopyPolicyCanKeepFiles() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        Path older = Files.createDirectories(repository.resolveSibling("06999aab-20260901T000000"));
        celeritas(older.resolve(STALE));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(stale, demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, false, false);

        assertEquals(List.of(demonica, bundled), candidates);
        assertTrue(stale.exists());
        assertTrue(Files.exists(older));
    }

    @Test
    void aFailureLeavesTheListAsItWas() throws IOException {
        List<File> candidates = sorted(celeritas(mods.resolve(INSTALLED)), celeritas(versionMods.resolve(STALE)),
            celeritas(versionMods.resolve(BUNDLED)), demonica(mods.resolve(DEMONICA), BUNDLED));
        List<File> copy = List.copyOf(candidates);

        BundledCeleritasHook.bansoukou(candidates, jar -> {
            throw new IllegalStateException("classifier failed");
        }, () -> true, true, true);

        assertEquals(copy, candidates);
        assertNull(BundledCeleritasHook.decision());
        assertTrue(Files.exists(versionMods.resolve(STALE)));
        assertTrue(Files.exists(versionMods.resolve(BUNDLED)));
    }

    @Test
    void aDedicatedServerLeavesOutTheBundledCopyAndKeepsTheInstalledOne() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(installed, demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> false, true, true);

        assertEquals(sorted(installed, demonica), candidates);
        assertTrue(bundled.exists() && installed.exists(), "a server deletes neither");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertFalse(decision.useBundled());
        assertEquals(List.of(INSTALLED), decision.externals());
        assertTrue(decision.report().startsWith("Dedicated server"), decision.report());
    }

    @Test
    void aDedicatedServerWithoutAnInstalledCeleritasLeavesOutTheBundledCopy() throws IOException {
        File bundled = celeritas(repository.resolve(BUNDLED));
        File demonica = demonica(mods.resolve(DEMONICA), BUNDLED);
        List<File> candidates = candidates(sorted(demonica), bundled);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> false, true, true);

        assertEquals(sorted(demonica), candidates);
        assertTrue(bundled.exists());
        assertFalse(BundledCeleritasHook.decision().useBundled());
    }

    @Test
    void aBuildWithoutANestedCeleritasChangesNothing() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File plain = plainMod(mods.resolve("plain.jar"));
        List<File> candidates = sorted(installed, plain);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, true);

        assertEquals(sorted(installed, plain), candidates);
        assertNull(BundledCeleritasHook.decision());
        assertNull(BundledCeleritasHook.decisionOrFallback());
        assertNull(BundledCeleritasHook.bundledJarName(), "the test classes are not a bundled jar");
        assertNull(BundledCeleritasHook.demonicaJarName(), "the test classes are not in a jar");
    }

    @Test
    void reconcileIgnoresEveryOtherCeleritasWhenTheBundledCopyIsUsed() {
        List<String> reparseable = new ArrayList<>(List.of(STALE, BUNDLED, "other-coremod.jar"));
        List<String> ignored = new ArrayList<>(List.of("lib.jar"));

        List<String> changed = BundledCeleritasHook.reconcile(reparseable, ignored, List.of(STALE, "my-renderer.jar", BUNDLED),
            BUNDLED, true, List.of(STALE));

        assertEquals(List.of(STALE, "my-renderer.jar"), changed);
        assertEquals(List.of(BUNDLED, "other-coremod.jar"), reparseable);
        assertEquals(List.of("lib.jar", STALE, "my-renderer.jar"), ignored);

        assertEquals(List.of(), BundledCeleritasHook.reconcile(reparseable, ignored, List.of(STALE, "my-renderer.jar", BUNDLED),
            BUNDLED, true, List.of(STALE)), "a second call changes nothing");
        assertEquals(List.of("lib.jar", STALE, "my-renderer.jar"), ignored);
    }

    /** The installed Celeritas takes precedence: the bundled copy, and stale copies, are what FML ignores. */
    @Test
    void reconcileIgnoresTheBundledCopyWhenAnInstalledOneTakesPrecedence() {
        List<String> reparseable = new ArrayList<>(List.of(INSTALLED, BUNDLED, STALE));
        List<String> ignored = new ArrayList<>();

        assertEquals(List.of(BUNDLED, STALE), BundledCeleritasHook.reconcile(reparseable, ignored, List.of(INSTALLED, STALE, BUNDLED),
            BUNDLED, false, List.of(STALE)));
        assertEquals(List.of(INSTALLED), reparseable, "the installed jar stays the identified Celeritas");
        assertEquals(List.of(BUNDLED, STALE), ignored);

        assertEquals(List.of(), BundledCeleritasHook.reconcile(reparseable, ignored, List.of(INSTALLED, STALE, BUNDLED), BUNDLED,
            false, List.of(STALE)));
        assertEquals(List.of(BUNDLED, STALE), ignored, "no name is added twice");
    }

    /** Cleanroom finds the hook by this exact signature and turns any throw into a crash of the launch. */
    @Test
    void theEntryPointIsWhatTheLoaderLooksFor() throws NoSuchMethodException {
        Method method = BundledCeleritasHook.class.getMethod("bansoukou", List.class);
        assertTrue(Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
        assertEquals(0, method.getExceptionTypes().length);
        for (Field field : BundledCeleritasHook.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                assertFalse(field.getType().getName().startsWith("org.apache.logging."),
                    field.getName() + ": the logger is obtained when used, never in the static initializer");
            }
        }
    }

    /**
     * The hook runs before any coremod, with a transformer exclusion: a reference to Minecraft, Forge or the rest of
     * Demonica would load that class untransformed, or fail. Only the launch class loader is allowed, for chaining.
     */
    @Test
    void theHookDependsOnNothingButTheJdkLog4jAndTheLaunchClassLoader() throws IOException, URISyntaxException {
        URL self = BundledCeleritasHook.class.getResource(BundledCeleritasHook.class.getSimpleName() + ".class");
        assertNotNull(self);
        assertEquals("file", self.getProtocol(), "the test reads the compiled classes from a directory");
        List<Path> classes;
        try (Stream<Path> files = Files.list(Path.of(self.toURI()).getParent())) {
            classes = files.filter(path -> path.getFileName().toString().startsWith("BundledCeleritasHook")
                && path.getFileName().toString().endsWith(".class")).sorted().toList();
        }
        assertTrue(classes.size() >= 3, "expected the hook, Decision and Plan: " + classes);
        for (Path path : classes) {
            String name = path.getFileName().toString();
            List<String> references;
            try (InputStream in = BundledCeleritasHook.class.getResourceAsStream(name)) {
                assertNotNull(in, name);
                references = symbolicReferences(in);
            }
            for (String reference : references) {
                if (reference.contains("net/minecraftforge/")
                    || reference.replace("net/minecraft/launchwrapper/", "").contains("net/minecraft/")
                    || reference.replace("com/demonica/loading/bundle/BundledCeleritasHook", "").contains("com/demonica/")) {
                    fail(name + " references " + reference);
                }
            }
            if (name.equals("BundledCeleritasHook.class")) {
                assertFalse(references.contains("<clinit>"), "the hook has no static initializer beyond constants");
            }
        }
    }

    /**
     * Every UTF-8 constant of a class file other than string literals: class names, descriptors, signatures, member
     * names. String literals are left out, since a resource name such as Minecraft's client entry point is no reference.
     */
    private static List<String> symbolicReferences(InputStream classFile) throws IOException {
        DataInputStream in = new DataInputStream(classFile);
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        Set<Integer> literals = new HashSet<>();
        for (int index = 1; index < count; index++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[index] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    index++;
                }
                case 7, 16, 19, 20 -> in.readUnsignedShort();
                case 8 -> literals.add(in.readUnsignedShort());
                case 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        List<String> references = new ArrayList<>();
        for (int index = 1; index < count; index++) {
            if (utf8[index] != null && !literals.contains(index)) {
                references.add(utf8[index]);
            }
        }
        return references;
    }

    private static List<File> sorted(File... files) {
        List<File> list = new ArrayList<>(List.of(files));
        // Cleanroom's order: by file name only, lower-cased.
        list.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Cleanroom's list: the mods folders' jars sorted, then the artifacts of its in-memory list (the bundled copy). */
    private static List<File> candidates(List<File> sortedModsJars, File... artifacts) {
        List<File> list = new ArrayList<>(sortedModsJars);
        list.addAll(List.of(artifacts));
        return list;
    }

    private static File celeritas(Path path) throws IOException {
        return jar(path, null, CELERITAS_CLASS, "", "mcmod.info",
            "[{\"modid\": \"celeritas\", \"name\": \"Celeritas\", \"version\": \"2.5.0-dev\"}]");
    }

    private static File celeritasCoremodOnly(Path path) throws IOException {
        Manifest manifest = manifest();
        manifest.getMainAttributes().putValue("FMLCorePlugin", BundledCeleritasHook.CELERITAS_PLUGIN);
        return jar(path, manifest, CELERITAS_CLASS, "");
    }

    private static File fat(Path path) throws IOException {
        return jar(path, null, CELERITAS_CLASS, "");
    }

    private static File plainMod(Path path) throws IOException {
        return jar(path, null, "com/example/Plain.class", "", "mcmod.info", "[{\"modid\": \"plain\"}]");
    }

    private static File demonica(Path path, String bundledName, String... libraries) throws IOException {
        Manifest manifest = manifest();
        Attributes attributes = manifest.getMainAttributes();
        List<String> contained = new ArrayList<>(List.of(libraries));
        contained.add(bundledName);
        attributes.putValue("ContainedDeps", String.join(" ", contained));
        attributes.putValue("NonModDeps", "true");
        attributes.putValue(BundledCeleritasHook.BANSOUKOU_ATTRIBUTE, BundledCeleritasHook.class.getName());
        attributes.putValue(BundledCeleritasHook.JAR_ATTRIBUTE, bundledName);
        attributes.putValue(BundledCeleritasHook.SHA256_ATTRIBUTE, "7d380743016c3c12a473a48ef15fba87812f66acf6ac19cd6251ab3055ba4471");
        attributes.putValue(BundledCeleritasHook.RELEASE_ATTRIBUTE, RELEASE);
        return jar(path, manifest, bundledName, "not read by the hook");
    }

    /** A jar that lists {@code containedDeps} in its manifest and names no Bansoukou hook. */
    private static File containing(Path path, String containedDeps) throws IOException {
        Manifest manifest = manifest();
        manifest.getMainAttributes().putValue("ContainedDeps", containedDeps);
        return jar(path, manifest, "com/example/Other.class", "");
    }

    private static Attributes hookNamed(String hook) {
        Attributes attributes = manifest().getMainAttributes();
        attributes.putValue(BundledCeleritasHook.BANSOUKOU_ATTRIBUTE, hook);
        return attributes;
    }

    private static File corrupt(Path path) throws IOException {
        return Files.write(path, "this is not a zip file".getBytes(StandardCharsets.UTF_8)).toFile();
    }

    private static Manifest manifest() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        return manifest;
    }

    /** A jar with the manifest (if any) and the entries, given as name and text pairs. */
    private static File jar(Path path, Manifest manifest, String... entries) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
             JarOutputStream jar = manifest != null ? new JarOutputStream(file, manifest) : new JarOutputStream(file)) {
            for (int i = 0; i < entries.length; i += 2) {
                jar.putNextEntry(new JarEntry(entries[i]));
                jar.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return path.toFile();
    }
}
