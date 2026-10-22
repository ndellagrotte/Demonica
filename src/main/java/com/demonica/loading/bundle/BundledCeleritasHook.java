package com.demonica.loading.bundle;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Decides which Celeritas the game loads when Demonica's jar carries one (docs/celeritas/PIN.md, "Bundled copy").
 *
 * <p>The nested jar's {@code .meta} sidecar names a Maven artifact, so Cleanroom extracts it to
 * {@code mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas/<version>/} ({@link #bundledArtifactFolder}),
 * not to {@code mods/1.12.2}, and lists it as a mod candidate only on a launch that read Demonica's own jar: the mods
 * folders are listed by name, the repository never is. Disable or remove Demonica's jar, and the extracted copy is
 * inert. Cleanroom then fails the launch with {@code DuplicateModsFoundException} if a second jar provides the mod id
 * {@code celeritas}: it has no rule that lets one copy win. The jar's {@code Bansoukou} manifest attribute names this
 * class, so Cleanroom's loader calls {@link #bansoukou(List)} with its live list of mod candidates after the extraction
 * and before any coremod is scanned. A Celeritas the player installed takes precedence: while one is on the list, the
 * bundled copy is dropped from it (its file stays, inert), and the installed jar is never touched. Without one, the
 * bundled copy is the Celeritas this game loads, and {@code MixinEarly} adds it to the class path at Demonica's own
 * place. Copies Demonica 0.8.2 and earlier extracted to {@code mods/1.12.2} (under this pin's name or an older one) are
 * dropped and deleted whenever another Celeritas is left, and older pins' folders in the repository are deleted. A
 * later fallback, {@code MixinEarly.injectData}, reconciles FML's own lists from the {@link #decision()} recorded
 * here, or, if this hook never ran, from the mods folders ({@link #decideWithoutHook}).
 *
 * <p>The loader loads this class with a transformer exclusion before any coremod exists, and turns anything this
 * class throws, from its static initializer on, into a crash of the launch. So the class has no static initializer
 * beyond constants, obtains its logger only when it logs, never throws out of {@link #bansoukou(List)}, and uses
 * nothing but the JDK, Log4j and, to pass the call on, the launch class loader: no Minecraft, Forge or other
 * Demonica class, none of which can be loaded safely this early.
 */
public final class BundledCeleritasHook {
    /**
     * The Maven group and artifact of the bundled copy, as build.gradle's sidecar names them: Cleanroom extracts it to
     * {@code mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas/<version>/<artifact>-<version>.jar}. The
     * version is {@code <sha8>-<timestamp>}, so the file keeps the name the pin encodes ({@link #BUNDLED_PREFIX}).
     */
    public static final String BUNDLED_GROUP = "com.demonica";
    public static final String BUNDLED_ARTIFACT = "zz-demonica-bundled-celeritas";
    /** The folder under {@code mods} that Cleanroom's in-memory artifact list extracts to. */
    public static final String REPOSITORY_FOLDER = "memory_repo";
    /**
     * The start of the nested jar's name; build.gradle's {@code bundledCeleritasJarName} must start with it too. The
     * loader orders its candidates by lower-cased file name, and {@code zz-} puts a copy in {@code mods/1.12.2} (which
     * Demonica 0.8.2 and earlier extracted there) after any Celeritas a player installs, so that when this hook did not
     * run, an installed jar is not beaten to the class path by such a copy's own place in that order.
     */
    public static final String BUNDLED_PREFIX = BUNDLED_ARTIFACT + "-";
    /** A class only Celeritas's mod has (the same one {@code Environment} looks for). */
    public static final String CELERITAS_MARKER = "org/taumc/celeritas/CeleritasVintage.class";
    /** Celeritas's coremod, as its manifest names it. */
    public static final String CELERITAS_PLUGIN = "org.taumc.celeritas.core.CeleritasLoadingPlugin";

    /** The attribute through which Cleanroom's loader calls a jar's hook. */
    public static final String BANSOUKOU_ATTRIBUTE = "Bansoukou";
    /** The name of the nested Celeritas jar, which is also its name once extracted. */
    public static final String JAR_ATTRIBUTE = "Demonica-Celeritas-Jar";
    /** The SHA-256 of the nested Celeritas jar. */
    public static final String SHA256_ATTRIBUTE = "Demonica-Celeritas-SHA256";
    /** The Celeritas-auto-build release the nested jar was taken from. */
    public static final String RELEASE_ATTRIBUTE = "Demonica-Celeritas-Release";
    /** The Maven coordinate the nested jar's sidecar gives it ({@code group:artifact:version}). */
    public static final String ARTIFACT_ATTRIBUTE = "Demonica-Celeritas-Artifact";

    /**
     * Whether copies of Celeritas that an older Demonica extracted to {@code mods/1.12.2} (under an older pin's name, or
     * this pin's name before 0.9.0 moved the extraction to the repository) are deleted from disk, rather than only left
     * out of this launch. Cleanroom never deletes an extracted jar, so without this they would stay, and a copy in
     * {@code mods/1.12.2} loads as a standalone Celeritas once Demonica's jar is disabled. Older pins' folders in the
     * repository are deleted too. The Celeritas jar the player installed is never deleted or changed, whatever this
     * says: it takes precedence over the bundled copy, and the log says how to use the bundled one instead. The bundled
     * copy's own extracted file is never deleted: inert while an installed Celeritas takes precedence, it is used
     * again, without another extraction, once that jar is removed.
     */
    static final boolean DELETE_STALE_BUNDLED = true;

    private static final String CONTAINED_DEPS_ATTRIBUTE = "ContainedDeps";
    private static final String CORE_PLUGIN_ATTRIBUTE = "FMLCorePlugin";
    /** Vanilla's client entry point; a dedicated server has no client classes. */
    private static final String CLIENT_MARKER = "net/minecraft/client/main/Main.class";
    private static final String LOGGER_NAME = "DemonicaBundle";

    // No initializers below: the defaults are what an un-run hook reports, and a static initializer could fail.
    private static volatile Decision decision;
    private static volatile Decision fallbackDecision;
    private static volatile String bundledJarName;
    private static volatile boolean bundledJarNameKnown;

    /**
     * What was decided for this launch.
     *
     * @param bundledName    the nested jar's name
     * @param useBundled     whether the bundled copy is the Celeritas this game loads
     * @param bundledPresent whether the bundled copy was among the candidates (before any deletion); false when it was
     *                       skipped or not extracted, and then removing an installed jar would leave no Celeritas at all
     * @param externals      the file names of the Celeritas jars the player installed, which take precedence over the
     *                       bundled copy and are never touched; empty when there is none
     * @param droppedStale   the file names of older Demonica's extracted copies in {@code mods/1.12.2} left out (and
     *                       deleted, by policy); the bundled name among them is the copy 0.8.2 and earlier extracted
     * @param report         one line for the log, saying what is used and what was left out
     * @param bundledFile    the bundled copy's file, when it is among the candidates; null otherwise
     */
    public record Decision(String bundledName, boolean useBundled, boolean bundledPresent, List<String> externals,
                           List<String> droppedStale, String report, File bundledFile) {
    }

    /**
     * The Celeritas candidates, sorted out.
     *
     * @param bundled       the bundled copy, null if it is not among the candidates (skipped, or not extracted); of
     *                      several files with its name, the one in the repository, else the one in {@code mods/1.12.2}
     * @param duplicates    the other files with the bundled copy's name (a player or a pack export copied it into
     *                      {@code mods}): the same jar, so never an installed Celeritas; left out, and never deleted,
     *                      since they are the player's files
     * @param externals     every Celeritas jar the player installed: every one that is neither the bundled copy nor
     *                      stale, in the candidates' order; these are never dropped or deleted
     * @param dropStale     copies an older Demonica extracted to {@code mods/1.12.2}: under another name, which no jar
     *                      names any more, or under the bundled name, which 0.8.2 and earlier extracted there; only
     *                      while another Celeritas is left (the bundled copy or an installed one)
     * @param useBundled    whether the bundled copy is the Celeritas this game loads: a client, the bundled copy present,
     *                      and nothing installed that takes precedence
     * @param dropBundled   whether the bundled copy is left out (an installed Celeritas, or a dedicated server); its
     *                      file is never deleted
     */
    record Plan(File bundled, List<File> duplicates, List<File> externals, List<File> dropStale, boolean useBundled,
                boolean dropBundled) {
    }

    private BundledCeleritasHook() {
    }

    /**
     * Cleanroom's entry point. {@code candidates} is the loader's live list of mod jars, sorted by file name; what is
     * removed from it is never scanned, added to the class path or identified as a mod. Never throws: a failure is
     * logged and leaves the list as it was, for the fallback in {@code MixinEarly} and the version gate to handle.
     */
    public static void bansoukou(List<File> candidates) {
        bansoukou(candidates, BundledCeleritasHook::isCeleritasJar, BundledCeleritasHook::isClient, DELETE_STALE_BUNDLED, true);
    }

    /** {@link #bansoukou(List)} with its classifier, side check and stale-copy policy given, for tests. */
    static void bansoukou(List<File> candidates, Predicate<File> isCeleritas, BooleanSupplier isClient, boolean deleteStale,
                          boolean chain) {
        Map<File, Attributes> manifests = null;
        File ownJar = null;
        try {
            manifests = mainAttributes(candidates);
            String self = BundledCeleritasHook.class.getName();
            String bundledName = null;
            String release = null;
            for (Map.Entry<File, Attributes> entry : manifests.entrySet()) {
                if (self.equals(trimmed(entry.getValue().getValue(BANSOUKOU_ATTRIBUTE)))) {
                    ownJar = entry.getKey();
                    bundledName = trimmed(entry.getValue().getValue(JAR_ATTRIBUTE));
                    release = trimmed(entry.getValue().getValue(RELEASE_ATTRIBUTE));
                    break;
                }
            }
            if (bundledName == null) {
                if (ownJar != null) {
                    logger().info("{} is not a bundled build of Demonica; the Celeritas mods are left as they are", ownJar.getName());
                } else {
                    logger().info("No mod jar names Demonica's Bansoukou hook; the Celeritas mods are left as they are");
                }
            } else {
                rememberBundledJarName(bundledName);
                Set<String> containedAnywhere = new HashSet<>();
                for (Attributes attributes : manifests.values()) {
                    containedAnywhere.addAll(containedDeps(attributes));
                }

                boolean client = isClient.getAsBoolean();
                Plan plan = plan(candidates, bundledName, client, isCeleritas, containedAnywhere);

                Set<File> drop = new HashSet<>(plan.dropStale());
                drop.addAll(plan.duplicates());
                if (plan.dropBundled()) {
                    drop.add(plan.bundled());
                }
                candidates.removeIf(drop::contains);
                List<File> staleVersions = List.of();
                if (deleteStale) {
                    for (File stale : plan.dropStale()) {
                        delete(stale);
                    }
                    staleVersions = deleteStaleVersions(plan.bundled());
                }

                String report = report(bundledName, release, client, plan, deleteStale, staleVersions);
                if (client && plan.bundled() == null && plan.externals().isEmpty()) {
                    logger().warn(report);
                } else {
                    logger().info(report);
                }
                decision = new Decision(bundledName, plan.useBundled(), plan.bundled() != null,
                    List.copyOf(names(plan.externals())), List.copyOf(names(plan.dropStale())), report, plan.bundled());
            }
        } catch (Throwable t) {
            try {
                logger().error("Demonica could not sort out the Celeritas mods; the list of mods is left as it was, and Demonica "
                    + "checks again before the mods are loaded", t);
            } catch (Throwable ignored) {
                // Nothing is left that could report it, and the launch must go on.
            }
        }
        if (chain) {
            chain(candidates, manifests, ownJar);
        }
    }

    /**
     * Sorts out the Celeritas candidates, in the candidates' order. {@code namesInAnyContainedDeps}: every name any
     * candidate's {@code ContainedDeps} lists, so a copy that another installed Demonica carries is never stale (it is
     * an installed Celeritas like any other). Directories and unreadable jars are left to {@code isCeleritas}, which
     * says they are not Celeritas.
     */
    static Plan plan(List<File> candidates, String bundledName, boolean client, Predicate<File> isCeleritas,
                     Set<String> namesInAnyContainedDeps) {
        List<File> named = new ArrayList<>();
        List<File> stale = new ArrayList<>();
        List<File> externals = new ArrayList<>();
        for (File candidate : candidates) {
            if (!isCeleritas.test(candidate)) {
                continue;
            }
            String name = candidate.getName();
            if (bundledName.equals(name)) {
                named.add(candidate);
            } else if (name.startsWith(BUNDLED_PREFIX) && !namesInAnyContainedDeps.contains(name) && inExtractionFolder(candidate)) {
                stale.add(candidate);
            } else {
                externals.add(candidate);
            }
        }
        // Every file with the bundled name is the bundled copy, wherever it is. Were a copy in mods counted as
        // installed, the advice to remove the "installed" jar would never end. Demonica's own extraction, in the
        // repository, is the one kept; the next best is a copy in mods/1.12.2 (Demonica 0.8.2 and earlier extracted
        // there), which is otherwise the leftover of that extraction: stale, and deleted, or it would load as a
        // standalone Celeritas once Demonica's jar is disabled. Any other copy is the player's, left out, never deleted.
        File bundled = null;
        for (File candidate : named) {
            if (bundled == null || rank(candidate) < rank(bundled)) {
                bundled = candidate;
            }
        }
        List<File> duplicates = new ArrayList<>();
        for (File candidate : named) {
            if (candidate.equals(bundled)) {
                continue;
            }
            if (inExtractionFolder(candidate)) {
                stale.add(candidate);
            } else {
                duplicates.add(candidate);
            }
        }
        boolean installed = !externals.isEmpty();
        boolean useBundled = client && bundled != null && !installed;
        // A stale copy is dropped only while another Celeritas is left, even one this side does not load: alone, it may
        // be the only Celeritas the player has, and the version gate judges whichever is loaded.
        List<File> dropStale = bundled != null || installed ? stale : List.of();
        return new Plan(bundled, List.copyOf(duplicates), List.copyOf(externals), List.copyOf(dropStale), useBundled,
            bundled != null && !useBundled);
    }

    /** The order of preference among files with the bundled name: the repository, then {@code mods/1.12.2}, then any. */
    private static int rank(File jar) {
        return inBundledRepository(jar) ? 0 : inExtractionFolder(jar) ? 1 : 2;
    }

    /**
     * Whether a jar is in {@code mods/1.12.2}, the folder Cleanroom extracts nested jars without a Maven coordinate to,
     * where Demonica 0.8.2 and earlier put the bundled copy. A copy with the bundled prefix, but not the bundled name,
     * anywhere else was put there by the player, so it is treated as one they installed, and never deleted.
     */
    private static boolean inExtractionFolder(File jar) {
        File parent = jar.getParentFile();
        return parent != null && "1.12.2".equals(parent.getName());
    }

    /**
     * Whether a jar is in a version folder of the bundled artifact's folder, {@code mods/memory_repo/com/demonica/
     * zz-demonica-bundled-celeritas/<version>/}, where Cleanroom extracts the bundled copy.
     */
    static boolean inBundledRepository(File jar) {
        File version = jar.getParentFile();
        File artifact = version != null ? version.getParentFile() : null;
        return artifact != null && isBundledArtifactFolder(artifact);
    }

    private static boolean isBundledArtifactFolder(File folder) {
        File group = folder.getParentFile();
        File top = group != null ? group.getParentFile() : null;
        File repository = top != null ? top.getParentFile() : null;
        return BUNDLED_ARTIFACT.equals(folder.getName()) && group != null && "demonica".equals(group.getName())
            && top != null && "com".equals(top.getName()) && repository != null && REPOSITORY_FOLDER.equals(repository.getName());
    }

    /**
     * {@code mods/memory_repo/com/demonica/zz-demonica-bundled-celeritas} under the game folder: the artifact folder
     * Cleanroom extracts the bundled copy to, one version folder per pin.
     */
    public static File bundledArtifactFolder(File minecraftHome) {
        return new File(new File(minecraftHome, "mods"), REPOSITORY_FOLDER + File.separator + BUNDLED_GROUP.replace('.', File.separatorChar)
            + File.separator + BUNDLED_ARTIFACT);
    }

    /**
     * The version folder of this Demonica's bundled copy, {@link #bundledArtifactFolder} plus the version its name
     * encodes; null in a build without a nested Celeritas.
     */
    public static File bundledVersionFolder(File minecraftHome) {
        String name = bundledJarName();
        return name != null ? new File(bundledArtifactFolder(minecraftHome), versionOf(name)) : null;
    }

    /** The version a bundled name encodes: the name without the prefix and {@code .jar}; the name itself if it has neither. */
    static String versionOf(String bundledName) {
        String version = bundledName.startsWith(BUNDLED_PREFIX) ? bundledName.substring(BUNDLED_PREFIX.length()) : bundledName;
        return version.endsWith(".jar") ? version.substring(0, version.length() - 4) : version;
    }

    /** Where the bundled copy is extracted to, as the player finds it: {@code mods/memory_repo/.../<version>/<name>}. */
    public static String extractedPath(String bundledName) {
        return "mods/" + REPOSITORY_FOLDER + "/" + BUNDLED_GROUP.replace('.', '/') + "/" + BUNDLED_ARTIFACT + "/" + versionOf(bundledName)
            + "/" + bundledName;
    }

    /**
     * Deletes the other version folders of the bundled artifact, older pins' copies, when the bundled copy is in its
     * repository folder. Nothing lists them, so nothing holds them open. Returns the folders deleted, or scheduled for
     * deletion at exit.
     */
    private static List<File> deleteStaleVersions(File bundled) {
        if (bundled == null || !inBundledRepository(bundled)) {
            return List.of();
        }
        File[] versions = bundled.getParentFile().getParentFile().listFiles(File::isDirectory);
        if (versions == null) {
            return List.of();
        }
        List<File> deleted = new ArrayList<>();
        for (File version : versions) {
            if (!version.equals(bundled.getParentFile())) {
                deleteFolder(version);
                deleted.add(version);
            }
        }
        return deleted;
    }

    /**
     * The decision for the fallback in {@code MixinEarly}, when this hook never ran: another mod's {@code Bansoukou}
     * hook sorted first, so the loader put every Celeritas on its list and the class path. {@code celeritasJars}: every
     * Celeritas mod jar in {@code mods} and {@code mods/1.12.2}. The jars are sorted out by the same rules as in
     * {@link #plan}, with the {@code ContainedDeps} of every jar in those folders; stale copies are deleted (by policy)
     * so that the next launch is right, except a leftover with the bundled name, which {@code NonModDeps} has put on
     * the class path: that one is deleted when the game exits. The decision is recorded for
     * {@link #decisionOrFallback()}. Throws only what reading the folders throws.
     */
    public static Decision decideWithoutHook(List<File> celeritasJars, String bundledName, boolean client) {
        // The folders to read ContainedDeps from: mods/1.12.2 and mods, found from the copies themselves, since only a
        // copy in mods/1.12.2 can be stale.
        Set<File> folders = new LinkedHashSet<>();
        for (File jar : celeritasJars) {
            if (inExtractionFolder(jar)) {
                folders.add(jar.getParentFile());
                if (jar.getParentFile().getParentFile() != null) {
                    folders.add(jar.getParentFile().getParentFile());
                }
            }
        }
        List<File> modJars = new ArrayList<>();
        for (File folder : folders) {
            File[] jars = folder.listFiles((parent, name) -> name.endsWith(".jar"));
            if (jars != null) {
                modJars.addAll(List.of(jars));
            }
        }
        Set<String> containedAnywhere = new HashSet<>();
        for (Attributes attributes : mainAttributes(modJars).values()) {
            containedAnywhere.addAll(containedDeps(attributes));
        }
        Plan plan = plan(celeritasJars, bundledName, client, jar -> true, containedAnywhere);
        List<File> staleVersions = List.of();
        if (DELETE_STALE_BUNDLED) {
            for (File stale : plan.dropStale()) {
                if (bundledName.equals(stale.getName())) {
                    // The leftover of the old extraction: NonModDeps has put it on the class path already, and the
                    // Celeritas classes may be loading from it (Linux deletes an open file, and the loader then fails).
                    deleteAtExit(stale);
                } else {
                    delete(stale);
                }
            }
            staleVersions = deleteStaleVersions(plan.bundled());
        }
        Decision fallback = new Decision(bundledName, plan.useBundled(), plan.bundled() != null,
            List.copyOf(names(plan.externals())), List.copyOf(names(plan.dropStale())),
            report(bundledName, null, client, plan, DELETE_STALE_BUNDLED, staleVersions), plan.bundled());
        fallbackDecision = fallback;
        return fallback;
    }

    /**
     * Brings FML's lists of jars in line with the decision: a jar on {@code ignored} and not on {@code reparseable} is
     * never identified as a mod. With the bundled copy in use, every other Celeritas jar is ignored. Otherwise the
     * bundled copy is, and so is every stale copy in {@code staleNames}: when the hook did not run they are candidates
     * (and a copy in {@code mods/1.12.2} is on the class path through Demonica's {@code NonModDeps} too), and FML would
     * identify one as a second {@code celeritas} next to the one the player installed. Changes the two lists in place,
     * without adding a name twice, and returns the names it ignored that were not ignored before; a second call changes
     * nothing.
     */
    public static List<String> reconcile(List<String> reparseable, List<String> ignored, Collection<String> celeritasJarNames,
                                         String bundledName, boolean useBundled, Collection<String> staleNames) {
        Set<String> toIgnore = new LinkedHashSet<>();
        if (useBundled) {
            for (String name : celeritasJarNames) {
                if (!name.equals(bundledName)) {
                    toIgnore.add(name);
                }
            }
        } else {
            toIgnore.add(bundledName);
            toIgnore.addAll(staleNames);
        }
        List<String> changed = new ArrayList<>();
        for (String name : toIgnore) {
            boolean wasReparseable = reparseable.removeIf(name::equals);
            boolean added = false;
            if (!ignored.contains(name)) {
                ignored.add(name);
                added = true;
            }
            if (added || wasReparseable) {
                changed.add(name);
            }
        }
        return changed;
    }

    /**
     * The names that come before {@code demonicaJarName} in the loader's order (lower-cased file names), in their own
     * order. When this hook did not run and a copy with the bundled name was still in {@code mods/1.12.2}, Demonica's
     * {@code NonModDeps} put that copy on the class path at the place of Demonica's own jar, and the first jar on the
     * class path supplies the Celeritas classes: a jar named here was added before that copy, and won. (The bundled copy
     * in the repository is listed after every jar of the mods folders, so it never wins against an installed jar.)
     */
    public static List<String> sortedBefore(Collection<String> names, String demonicaJarName) {
        String key = demonicaJarName.toLowerCase(Locale.ROOT);
        List<String> before = new ArrayList<>();
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).compareTo(key) < 0) {
                before.add(name);
            }
        }
        return before;
    }

    /**
     * Whether a jar is a Celeritas mod: it has Celeritas's main class, and either declares the mod id {@code celeritas}
     * or is Celeritas's coremod. A jar that only shades Celeritas's classes is not; neither is a directory or a jar that
     * cannot be read.
     */
    public static boolean isCeleritasJar(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try (JarFile jar = new JarFile(file, false)) {
            if (jar.getEntry(CELERITAS_MARKER) == null) {
                return false;
            }
            Manifest manifest = jar.getManifest();
            if (manifest != null && CELERITAS_PLUGIN.equals(trimmed(manifest.getMainAttributes().getValue(CORE_PLUGIN_ATTRIBUTE)))) {
                return true;
            }
            ZipEntry info = jar.getEntry("mcmod.info");
            if (info == null) {
                return false;
            }
            try (InputStream in = jar.getInputStream(info)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return Pattern.compile("\"modid\"\\s*:\\s*\"celeritas\"").matcher(text).find();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** What the hook decided, or null if it did not run in this launch (or failed). */
    public static Decision decision() {
        return decision;
    }

    /**
     * What the hook decided or, when it did not run, what the fallback in {@code MixinEarly} decided
     * ({@link #decideWithoutHook}); null if neither did.
     */
    public static Decision decisionOrFallback() {
        Decision hook = decision;
        return hook != null ? hook : fallbackDecision;
    }

    /**
     * The name of the Celeritas jar nested in this Demonica, from the jar the hook found or else from the jar this class
     * was loaded from; null in a development workspace or any build without a nested Celeritas. Never throws.
     */
    public static String bundledJarName() {
        if (!bundledJarNameKnown) {
            synchronized (BundledCeleritasHook.class) {
                if (!bundledJarNameKnown) {
                    String name = null;
                    try {
                        Attributes attributes = ownJarAttributes();
                        name = attributes != null ? trimmed(attributes.getValue(JAR_ATTRIBUTE)) : null;
                    } catch (Throwable ignored) {
                        // No jar to read it from: not a bundled build, as far as anything can tell.
                    }
                    bundledJarName = name;
                    bundledJarNameKnown = true;
                }
            }
        }
        return bundledJarName;
    }

    /** Forgets the decision and the cached jar name, for tests. */
    static synchronized void resetForTests() {
        decision = null;
        fallbackDecision = null;
        bundledJarName = null;
        bundledJarNameKnown = false;
    }

    private static synchronized void rememberBundledJarName(String name) {
        bundledJarName = name;
        bundledJarNameKnown = true;
    }

    private static Logger logger() {
        return LogManager.getLogger(LOGGER_NAME);
    }

    private static boolean isClient() {
        ClassLoader loader = BundledCeleritasHook.class.getClassLoader();
        if (loader != null && loader.getResource(CLIENT_MARKER) != null) {
            return true;
        }
        return ClassLoader.getSystemResource(CLIENT_MARKER) != null;
    }

    private static String report(String bundledName, String release, boolean client, Plan plan, boolean deleteStale,
                                 List<File> staleVersions) {
        StringBuilder report = new StringBuilder();
        String bundled = bundledName + (release != null ? " (Celeritas-auto-build " + release + ")" : "");
        String folder = "mods/" + REPOSITORY_FOLDER + "/" + BUNDLED_GROUP.replace('.', '/') + "/" + BUNDLED_ARTIFACT;
        if (!client) {
            report.append("Dedicated server: Celeritas is client-only, so the Celeritas bundled with Demonica, ").append(bundled)
                .append(", is not loaded.");
        } else if (!plan.externals().isEmpty()) {
            List<String> paths = new ArrayList<>();
            for (File external : plan.externals()) {
                paths.add(path(external));
            }
            boolean one = paths.size() == 1;
            if (plan.bundled() != null) {
                report.append("Using the installed ").append(String.join(" and ", paths))
                    .append(" instead of the Celeritas bundled with Demonica; remove ").append(one ? "it" : "them")
                    .append(" from the mods folder to use the bundled build.");
            } else {
                // Removing the installed jar would leave no Celeritas at all: the bundled copy is not there to take over.
                report.append("Using the installed ").append(String.join(" and ", paths))
                    .append("; the Celeritas bundled with Demonica, ").append(bundled)
                    .append(", is not in ").append(folder).append(" (it was not extracted, or -Dfml.skipContainedDeps names it).");
            }
            if (!one) {
                report.append(" FML cannot load more than one Celeritas.");
            }
        } else if (plan.bundled() == null) {
            report.append("The Celeritas bundled with Demonica, ").append(bundled).append(", is not in ").append(folder)
                .append(" (it was not extracted, or -Dfml.skipContainedDeps names it), and no other Celeritas is installed; ")
                .append("Demonica checks whether any Celeritas that loads is the build it was made for.");
        } else {
            report.append("Using the Celeritas bundled with Demonica, ").append(bundled).append('.');
        }
        if (!plan.dropStale().isEmpty()) {
            report.append(deleteStale ? " Removed " : " Left out ").append(plan.dropStale().size())
                .append(plan.dropStale().size() == 1 ? " copy" : " copies")
                .append(" of Celeritas an earlier Demonica extracted to mods/1.12.2: ").append(String.join(", ", names(plan.dropStale())))
                .append('.');
        }
        if (!staleVersions.isEmpty()) {
            report.append(" Removed ").append(staleVersions.size()).append(staleVersions.size() == 1 ? " older pin" : " older pins")
                .append(" from ").append(folder).append(": ").append(String.join(", ", names(staleVersions))).append('.');
        }
        return report.toString();
    }

    /** {@code mods/<name>}, {@code mods/1.12.2/<name>} or {@code mods/memory_repo/.../<version>/<name>}, as the player finds it. */
    private static String path(File jar) {
        if (inBundledRepository(jar)) {
            return "mods/" + REPOSITORY_FOLDER + "/" + BUNDLED_GROUP.replace('.', '/') + "/" + BUNDLED_ARTIFACT + "/"
                + jar.getParentFile().getName() + "/" + jar.getName();
        }
        File parent = jar.getParentFile();
        if (parent == null) {
            return jar.getName();
        }
        File grandparent = parent.getParentFile();
        String prefix = "1.12.2".equals(parent.getName()) && grandparent != null ? grandparent.getName() + "/1.12.2/"
            : parent.getName() + "/";
        return prefix + jar.getName();
    }

    private static void delete(File jar) {
        try {
            Files.delete(jar.toPath());
        } catch (IOException | RuntimeException e) {
            // On Windows the jar may still be open (always, on the fallback path, where it is on the class path).
            try {
                jar.deleteOnExit();
            } catch (RuntimeException ignored) {
                // Then it stays, and is found again next time.
            }
            logger().info("Could not delete {} now ({}); it is deleted when the game exits", path(jar), e.toString());
        }
    }

    /** Schedules a jar that is on the class path for deletion when the game exits (on Windows it may stay open and remain). */
    private static void deleteAtExit(File jar) {
        try {
            jar.deleteOnExit();
            logger().info("{} is on the class path; it is deleted when the game exits", path(jar));
        } catch (RuntimeException e) {
            logger().info("Could not schedule {} for deletion ({}); it is found again next time", path(jar), e.toString());
        }
    }

    /** Deletes a folder and everything in it (an older pin's version folder: its jar and {@code .meta}); logs a failure. */
    private static void deleteFolder(File folder) {
        try (Stream<Path> paths = Files.walk(folder.toPath())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException | RuntimeException e) {
            logger().info("Could not delete {} ({}); it is tried again next time", folder, e.toString());
        }
    }

    /**
     * Passes the call on to the hook of the first other jar that carries one: the loader honours only the first, in
     * file name order, and that is Demonica's when this runs, so another mod's hook would otherwise never run. Does what
     * the loader does for its own hook. A hook that names this class (a second Demonica) is not called again.
     */
    private static void chain(List<File> candidates, Map<File, Attributes> manifests, File ownJar) {
        try {
            LaunchClassLoader loader = Launch.classLoader;
            if (loader == null || manifests == null) {
                return;
            }
            Map.Entry<File, String> target = chainTarget(candidates, manifests, ownJar);
            if (target == null) {
                return;
            }
            File jar = target.getKey();
            String hook = target.getValue();
            try {
                loader.addURL(jar.toURI().toURL());
                loader.addTransformerExclusion(hook);
                Method method = Class.forName(hook, true, loader).getMethod("bansoukou", List.class);
                method.invoke(null, candidates);
                logger().info("Passed the list of mods on to {}'s Bansoukou hook {}", jar.getName(), hook);
            } catch (Throwable t) {
                logger().error("{}'s Bansoukou hook {} failed; the launch goes on without it", jar.getName(), hook, t);
            }
        } catch (Throwable t) {
            try {
                logger().error("Could not pass the list of mods on to another mod's Bansoukou hook", t);
            } catch (Throwable ignored) {
                // The launch must go on.
            }
        }
    }

    /**
     * The jar whose hook {@link #chain} calls, with that hook's class name: the first jar, in the order of
     * {@code manifests}, that names a hook other than this class, is not Demonica's own jar, and is still on the list
     * (a jar this hook dropped is not scanned, so its hook is not called either). Null when there is none.
     */
    static Map.Entry<File, String> chainTarget(List<File> candidates, Map<File, Attributes> manifests, File ownJar) {
        String self = BundledCeleritasHook.class.getName();
        for (Map.Entry<File, Attributes> entry : manifests.entrySet()) {
            File jar = entry.getKey();
            String hook = trimmed(entry.getValue().getValue(BANSOUKOU_ATTRIBUTE));
            if (hook == null || hook.equals(self) || jar.equals(ownJar) || !candidates.contains(jar)) {
                continue;
            }
            return Map.entry(jar, hook);
        }
        return null;
    }

    /** The main manifest attributes of every candidate jar that has a manifest, in the candidates' order. */
    private static Map<File, Attributes> mainAttributes(List<File> candidates) {
        Map<File, Attributes> manifests = new LinkedHashMap<>();
        for (File candidate : candidates) {
            if (candidate == null || !candidate.isFile()) {
                continue;
            }
            try (JarFile jar = new JarFile(candidate, false)) {
                Manifest manifest = jar.getManifest();
                if (manifest != null) {
                    manifests.put(candidate, manifest.getMainAttributes());
                }
            } catch (IOException | RuntimeException e) {
                // Not a readable jar; the loader reports it on its own.
            }
        }
        return manifests;
    }

    /**
     * The file name of the jar this class was loaded from, which is Demonica's own; null in a development workspace,
     * where it is loaded from a directory. The fallback in {@code MixinEarly} needs it: Demonica's {@code NonModDeps}
     * puts a copy left in {@code mods/1.12.2} on the class path at this name's place in the loader's order. Never throws.
     */
    public static String demonicaJarName() {
        try {
            File jar = ownJar();
            return jar != null ? jar.getName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The main manifest attributes of the jar this class was loaded from; null if it was not loaded from a jar. */
    private static Attributes ownJarAttributes() throws IOException {
        File jar = ownJar();
        if (jar == null) {
            return null;
        }
        try (JarFile file = new JarFile(jar, false)) {
            Manifest manifest = file.getManifest();
            return manifest != null ? manifest.getMainAttributes() : null;
        }
    }

    /** The jar this class was loaded from, from its code source or else its class file's URL; null if not a jar. */
    private static File ownJar() {
        File jar = null;
        CodeSource source = BundledCeleritasHook.class.getProtectionDomain().getCodeSource();
        URL location = source != null ? source.getLocation() : null;
        if (location != null && "file".equals(location.getProtocol())) {
            try {
                File file = new File(location.toURI());
                jar = file.isFile() ? file : null;
            } catch (Exception ignored) {
                // Fall back to the class file's own URL.
            }
        }
        if (jar == null) {
            URL self = BundledCeleritasHook.class.getResource(BundledCeleritasHook.class.getSimpleName() + ".class");
            if (self != null && "jar".equals(self.getProtocol())) {
                String path = self.getPath();
                int separator = path.indexOf("!/");
                if (separator > 0) {
                    try {
                        URI uri = new URI(path.substring(0, separator));
                        jar = "file".equals(uri.getScheme()) ? new File(uri) : null;
                    } catch (Exception ignored) {
                        // Not a file on disk.
                    }
                }
            }
        }
        return jar != null && jar.isFile() ? jar : null;
    }

    /** The names a manifest's {@code ContainedDeps} lists, separated by spaces as the loader reads them. */
    private static List<String> containedDeps(Attributes attributes) {
        String value = attributes.getValue(CONTAINED_DEPS_ATTRIBUTE);
        List<String> names = new ArrayList<>();
        if (value != null) {
            for (String name : value.trim().split("\\s+")) {
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static List<String> names(List<File> files) {
        List<String> names = new ArrayList<>(files.size());
        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
