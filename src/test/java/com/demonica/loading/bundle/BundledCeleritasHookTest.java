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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The hook that makes the Celeritas nested in Demonica's jar win over any other (docs/celeritas/PIN.md, "Bundled
 * copy"): what it leaves out of Cleanroom's list of candidates, what it deletes and never deletes, the reconciliation of
 * FML's lists in {@code MixinEarly}, and the constraints that come from running before any coremod.
 */
class BundledCeleritasHookTest {
    private static final String BUNDLED = "0-demonica-bundled-celeritas-9b661b70-20260926T020851.jar";
    private static final String STALE = "0-demonica-bundled-celeritas-06999aab-20260901T000000.jar";
    private static final String OTHER_DEMONICAS = "0-demonica-bundled-celeritas-12345678-20261001T000000.jar";
    private static final String INSTALLED = "01-celeritas-forge-mc12.2-2.5.0-dev.jar";
    private static final String CELERITAS_CLASS = BundledCeleritasHook.CELERITAS_MARKER;
    private static final String RELEASE = "commit-9b661b70ecaf84c39052e8fbc51c875ba37e739d-20260926T020851";

    @TempDir
    Path home;
    private Path mods;
    private Path versionMods;

    @BeforeEach
    void layOut() throws IOException {
        BundledCeleritasHook.resetForTests();
        mods = Files.createDirectories(home.resolve("mods"));
        versionMods = Files.createDirectories(mods.resolve("1.12.2"));
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
    void planDropsTheInstalledAndStaleCopiesInOrder() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File renamed = celeritas(mods.resolve("zz-my-renderer.jar"));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        List<File> candidates = sorted(installed, renamed, stale, bundled, demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED),
            plainMod(mods.resolve("plain.jar")));

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(List.of(installed, renamed), plan.dropExternal());
        assertEquals(List.of(stale), plan.dropStale());
        assertFalse(plan.dropBundled());
    }

    @Test
    void withoutUsingTheBundledCopyOnlyItAndStaleCopiesAreDropped() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        List<File> candidates = sorted(installed, stale, bundled);

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, false, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(List.of(), plan.dropExternal());
        assertEquals(List.of(stale), plan.dropStale(), "a dedicated server still cleans up after earlier pins");
        assertTrue(plan.dropBundled());
    }

    @Test
    void withoutTheBundledCopyNothingIsDropped() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        List<File> candidates = sorted(celeritas(mods.resolve(INSTALLED)), stale, plainMod(mods.resolve("plain.jar")));

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(List.of(), plan.dropExternal());
        assertEquals(List.of(), plan.dropStale(), "a stale copy may be the only Celeritas left");
        assertFalse(plan.dropBundled());
    }

    @Test
    void withoutTheBundledCopyTheHookKeepsAStaleCopy() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File demonica = demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED);
        List<File> candidates = sorted(stale, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(sorted(stale, demonica), candidates);
        assertTrue(stale.exists(), "the only Celeritas on disk is never deleted");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertFalse(decision.useBundled());
        assertEquals(List.of(), decision.droppedStale());
    }

    /** Cleanroom extracts only to mods/1.12.2, so a prefixed copy in mods was put there by the player. */
    @Test
    void aPrefixedCopyOutsideTheExtractionFolderIsLeftOutButNeverDeleted() throws IOException {
        File placed = celeritas(mods.resolve(STALE));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        File demonica = demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED);
        List<File> candidates = sorted(placed, bundled, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertEquals(sorted(bundled, demonica), candidates);
        assertTrue(placed.exists());
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertEquals(List.of(STALE), decision.droppedExternal());
        assertEquals(List.of(), decision.droppedStale());
        assertTrue(decision.report().contains("mods/" + STALE + ": Demonica brings its own Celeritas, and that jar can be removed"),
            decision.report());
    }

    @Test
    void aCopyAnotherInstalledDemonicaCarriesIsNotStale() throws IOException {
        File others = celeritas(versionMods.resolve(OTHER_DEMONICAS));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        List<File> candidates = sorted(others, bundled);

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED, OTHER_DEMONICAS));

        assertEquals(List.of(), plan.dropStale(), "never deleted while a jar in the mods folder names it");
        assertEquals(List.of(others), plan.dropExternal(), "but still a second Celeritas, left out of this launch");
    }

    @Test
    void keepsWhatIsNotACeleritasMod() throws IOException {
        List<File> candidates = sorted(celeritas(versionMods.resolve(BUNDLED)), plainMod(versionMods.resolve(STALE)),
            fat(mods.resolve("fat.jar")), corrupt(mods.resolve("corrupt.jar")),
            Files.createDirectories(mods.resolve("exploded")).toFile());

        BundledCeleritasHook.Plan plan = BundledCeleritasHook.plan(candidates, BUNDLED, true, BundledCeleritasHook::isCeleritasJar,
            Set.of(BUNDLED));

        assertEquals(List.of(), plan.dropExternal());
        assertEquals(List.of(), plan.dropStale(), "a prefixed jar that is not Celeritas is not Demonica's");
    }

    @Test
    void theHookLeavesOutTheOthersDeletesOnlyStaleCopiesAndReports() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        File demonica = demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED, "jcpp-1.4.14.jar");
        File plain = plainMod(mods.resolve("plain.jar"));
        List<File> candidates = sorted(installed, stale, bundled, demonica, plain);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, true);

        assertEquals(sorted(bundled, demonica, plain), candidates);
        assertFalse(stale.exists(), "a copy an earlier Demonica extracted is deleted");
        assertTrue(installed.exists(), "the jar the player installed is never touched");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertEquals(BUNDLED, decision.bundledName());
        assertTrue(decision.useBundled());
        assertEquals(List.of(INSTALLED), decision.droppedExternal());
        assertEquals(List.of(STALE), decision.droppedStale());
        assertTrue(decision.report().contains(INSTALLED), decision.report());
        assertTrue(decision.report().contains(STALE), decision.report());
        assertTrue(decision.report().contains("can be removed"), decision.report());
        assertEquals(BUNDLED, BundledCeleritasHook.bundledJarName());
    }

    /**
     * The hook's one destructive action, end to end: the names it must not delete come from the manifests it reads, so
     * a second jar's ContainedDeps (space-separated, more than one entry) protects the copy it carries.
     */
    @Test
    void theHookNeverDeletesACopyAnotherInstalledJarCarries() throws IOException {
        File others = celeritas(versionMods.resolve(OTHER_DEMONICAS));
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        File own = demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED);
        File second = containing(mods.resolve("demonica-0.7.9-fork.jar"), "jcpp-1.4.14.jar " + OTHER_DEMONICAS);
        List<File> candidates = sorted(others, stale, bundled, own, second);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, false);

        assertTrue(others.exists(), "another installed jar names it");
        assertFalse(stale.exists(), "no jar names it");
        assertFalse(candidates.contains(others), "still a second Celeritas, left out of this launch");
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertEquals(List.of(OTHER_DEMONICAS), decision.droppedExternal());
        assertEquals(List.of(STALE), decision.droppedStale());
    }

    /** The fallback in MixinEarly, when the hook never ran: the same rules, with the mods folders read from disk. */
    @Test
    void deletingStaleCopiesWithoutTheHookKeepsWhatIsNamedOrInstalled() throws IOException {
        File others = celeritas(versionMods.resolve(OTHER_DEMONICAS));
        File stale = celeritas(versionMods.resolve(STALE));
        File placed = celeritas(mods.resolve("0-demonica-bundled-celeritas-00000000-20250101T000000.jar"));
        File installed = celeritas(mods.resolve(INSTALLED));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED);
        containing(mods.resolve("demonica-0.7.9-fork.jar"), "jcpp-1.4.14.jar " + OTHER_DEMONICAS);

        List<String> removed = BundledCeleritasHook.deleteStaleCopies(List.of(placed, installed, others, stale, bundled), BUNDLED);

        assertEquals(List.of(STALE), removed);
        assertFalse(stale.exists());
        assertTrue(others.exists() && placed.exists() && installed.exists() && bundled.exists());
    }

    @Test
    void deletingStaleCopiesWithoutTheBundledCopyDeletesNothing() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));

        assertEquals(List.of(), BundledCeleritasHook.deleteStaleCopies(List.of(stale), BUNDLED));
        assertTrue(stale.exists());
        assertEquals(List.of(), BundledCeleritasHook.deleteStaleCopies(List.of(stale), null));
    }

    @Test
    void chainingPicksTheFirstOtherHookStillOnTheList() {
        File own = mods.resolve("demonica-0.8.0.jar").toFile();
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
        File own = mods.resolve("demonica-0.8.0.jar").toFile();
        File other = mods.resolve("other.jar").toFile();
        Map<File, Attributes> manifests = new LinkedHashMap<>();
        manifests.put(own, hookNamed("com.example.Renamed"));
        manifests.put(other, hookNamed("com.example.OtherHook"));

        assertEquals(Map.entry(other, "com.example.OtherHook"), BundledCeleritasHook.chainTarget(List.of(own, other), manifests, own));
    }

    @Test
    void theStaleCopyPolicyCanKeepFiles() throws IOException {
        File stale = celeritas(versionMods.resolve(STALE));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        File demonica = demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED);
        List<File> candidates = sorted(stale, bundled, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, false, false);

        assertEquals(sorted(bundled, demonica), candidates);
        assertTrue(stale.exists());
    }

    @Test
    void aFailureLeavesTheListAsItWas() throws IOException {
        List<File> candidates = sorted(celeritas(mods.resolve(INSTALLED)), celeritas(versionMods.resolve(STALE)),
            celeritas(versionMods.resolve(BUNDLED)), demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED));
        List<File> copy = List.copyOf(candidates);

        BundledCeleritasHook.bansoukou(candidates, jar -> {
            throw new IllegalStateException("classifier failed");
        }, () -> true, true, true);

        assertEquals(copy, candidates);
        assertNull(BundledCeleritasHook.decision());
        assertTrue(Files.exists(versionMods.resolve(STALE)));
    }

    @Test
    void aDedicatedServerLeavesOutTheBundledCopyAndKeepsTheInstalledOne() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File bundled = celeritas(versionMods.resolve(BUNDLED));
        File demonica = demonica(mods.resolve("demonica-0.8.0.jar"), BUNDLED);
        List<File> candidates = sorted(installed, bundled, demonica);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> false, true, true);

        assertEquals(sorted(installed, demonica), candidates);
        assertTrue(bundled.exists() && installed.exists());
        BundledCeleritasHook.Decision decision = BundledCeleritasHook.decision();
        assertNotNull(decision);
        assertFalse(decision.useBundled());
        assertEquals(List.of(), decision.droppedExternal());
    }

    @Test
    void aBuildWithoutANestedCeleritasChangesNothing() throws IOException {
        File installed = celeritas(mods.resolve(INSTALLED));
        File plain = plainMod(mods.resolve("plain.jar"));
        List<File> candidates = sorted(installed, plain);

        BundledCeleritasHook.bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, () -> true, true, true);

        assertEquals(sorted(installed, plain), candidates);
        assertNull(BundledCeleritasHook.decision());
        assertNull(BundledCeleritasHook.bundledJarName(), "the test classes are not a bundled jar");
    }

    @Test
    void reconcileIgnoresEveryOtherCeleritasWhenTheBundledCopyIsUsed() {
        List<String> reparseable = new ArrayList<>(List.of(INSTALLED, BUNDLED, "other-coremod.jar"));
        List<String> ignored = new ArrayList<>(List.of("lib.jar"));

        List<String> changed = BundledCeleritasHook.reconcile(reparseable, ignored, List.of(INSTALLED, "zz-renamed.jar", BUNDLED),
            BUNDLED, true);

        assertEquals(List.of(INSTALLED, "zz-renamed.jar"), changed);
        assertEquals(List.of(BUNDLED, "other-coremod.jar"), reparseable);
        assertEquals(List.of("lib.jar", INSTALLED, "zz-renamed.jar"), ignored);

        assertEquals(List.of(), BundledCeleritasHook.reconcile(reparseable, ignored, List.of(INSTALLED, "zz-renamed.jar", BUNDLED),
            BUNDLED, true), "a second call changes nothing");
        assertEquals(List.of("lib.jar", INSTALLED, "zz-renamed.jar"), ignored);
    }

    @Test
    void reconcileIgnoresTheBundledCopyWhenItIsNotUsed() {
        List<String> reparseable = new ArrayList<>(List.of(INSTALLED, BUNDLED));
        List<String> ignored = new ArrayList<>();

        assertEquals(List.of(BUNDLED), BundledCeleritasHook.reconcile(reparseable, ignored, List.of(INSTALLED, BUNDLED), BUNDLED, false));
        assertEquals(List.of(INSTALLED), reparseable);
        assertEquals(List.of(BUNDLED), ignored);

        assertEquals(List.of(), BundledCeleritasHook.reconcile(reparseable, ignored, List.of(INSTALLED, BUNDLED), BUNDLED, false));
        assertEquals(List.of(BUNDLED), ignored, "no name is added twice");
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
        attributes.putValue(BundledCeleritasHook.SHA256_ATTRIBUTE, "80f07935d86d364fd9b80297f4bc87d47f3a49209f589091b554a7332b56a909");
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
