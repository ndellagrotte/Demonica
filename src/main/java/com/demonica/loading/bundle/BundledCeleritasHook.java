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
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Makes the Celeritas nested in Demonica's jar the one the game loads (docs/celeritas/PIN.md, "Bundled copy").
 *
 * <p>Cleanroom extracts the nested jar to {@code mods/1.12.2} before any mod code runs, and then fails the launch with
 * {@code DuplicateModsFoundException} if a second jar provides the mod id {@code celeritas}: it has no rule that lets
 * one copy win. The jar's {@code Bansoukou} manifest attribute names this class, so Cleanroom's loader calls
 * {@link #bansoukou(List)} with its live list of mod candidates after the extraction and before any coremod is
 * scanned. This drops every other Celeritas from that list: one the player installed (the file is left where it is)
 * and the copies older Demonica versions extracted under another name (those are deleted). A later fallback,
 * {@code MixinEarly.injectData}, reconciles FML's own lists from the {@link #decision()} recorded here, or from the
 * mods folders if this hook never ran.
 *
 * <p>The loader loads this class with a transformer exclusion before any coremod exists, and turns anything this
 * class throws, from its static initializer on, into a crash of the launch. So the class has no static initializer
 * beyond constants, obtains its logger only when it logs, never throws out of {@link #bansoukou(List)}, and uses
 * nothing but the JDK, Log4j and, to pass the call on, the launch class loader: no Minecraft, Forge or other
 * Demonica class, none of which can be loaded safely this early.
 */
public final class BundledCeleritasHook {
    /** The start of the nested jar's name; build.gradle's {@code bundledCeleritasJarName} must start with it too. */
    public static final String BUNDLED_PREFIX = "0-demonica-bundled-celeritas-";
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

    /**
     * Whether copies of Celeritas that an older Demonica extracted under another name are deleted from disk, rather than
     * only left out of this launch. Cleanroom never deletes an extracted jar, so without this every update of the pin
     * would leave one more copy behind. The Celeritas jar the player installed is never deleted or changed, whatever
     * this says: it is only left out of the launch, and the log says it can be removed. This is the one place to change
     * either policy.
     */
    static final boolean DELETE_STALE_BUNDLED = true;

    private static final String CONTAINED_DEPS_ATTRIBUTE = "ContainedDeps";
    private static final String CORE_PLUGIN_ATTRIBUTE = "FMLCorePlugin";
    /** Vanilla's client entry point; a dedicated server has no client classes. */
    private static final String CLIENT_MARKER = "net/minecraft/client/main/Main.class";
    private static final String LOGGER_NAME = "DemonicaBundle";

    // No initializers below: the defaults are what an un-run hook reports, and a static initializer could fail.
    private static volatile Decision decision;
    private static volatile String bundledJarName;
    private static volatile boolean bundledJarNameKnown;

    /**
     * What the hook decided for this launch.
     *
     * @param bundledName     the nested jar's name
     * @param useBundled      whether the bundled copy is the Celeritas this game loads
     * @param droppedExternal the file names of the other Celeritas jars left out of the launch (never deleted)
     * @param droppedStale    the file names of older Demonica's extracted copies left out (and deleted, by policy)
     * @param report          one line for the log, saying what is used and what was left out
     */
    public record Decision(String bundledName, boolean useBundled, List<String> droppedExternal, List<String> droppedStale,
                           String report) {
    }

    /**
     * Which candidates to leave out.
     *
     * @param dropExternal every Celeritas jar other than the bundled copy, when the bundled copy is used
     * @param dropStale    copies an older Demonica extracted to {@code mods/1.12.2} under another name, which no installed
     *                     jar names any more; only while the bundled copy is on the list
     * @param dropBundled  whether the bundled copy itself is left out (a dedicated server)
     */
    record Plan(List<File> dropExternal, List<File> dropStale, boolean dropBundled) {
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
                boolean present = false;
                for (File candidate : candidates) {
                    if (bundledName.equals(candidate.getName()) && isCeleritas.test(candidate)) {
                        present = true;
                        break;
                    }
                }
                boolean useBundled = client && present;
                Plan plan = plan(candidates, bundledName, useBundled, isCeleritas, containedAnywhere);

                Set<File> drop = new HashSet<>(plan.dropExternal());
                drop.addAll(plan.dropStale());
                String bundled = bundledName;
                candidates.removeIf(candidate -> drop.contains(candidate)
                    || plan.dropBundled() && bundled.equals(candidate.getName()));
                if (deleteStale) {
                    for (File stale : plan.dropStale()) {
                        delete(stale);
                    }
                }

                List<String> external = names(plan.dropExternal());
                List<String> staleNames = names(plan.dropStale());
                String report = report(bundledName, release, client, present, plan, deleteStale);
                if (!external.isEmpty() || client && !present) {
                    logger().warn(report);
                } else {
                    logger().info(report);
                }
                decision = new Decision(bundledName, useBundled, List.copyOf(external), List.copyOf(staleNames), report);
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
     * Which candidates to leave out, in the candidates' order. {@code namesInAnyContainedDeps}: every name any
     * candidate's {@code ContainedDeps} lists, so a copy that another installed Demonica carries is never stale.
     * Directories and unreadable jars are left to {@code isCeleritas}, which says they are not Celeritas.
     */
    static Plan plan(List<File> candidates, String bundledName, boolean useBundled, Predicate<File> isCeleritas,
                     Set<String> namesInAnyContainedDeps) {
        List<File> celeritas = new ArrayList<>();
        File bundled = null;
        for (File candidate : candidates) {
            if (isCeleritas.test(candidate)) {
                if (bundled == null && bundledName.equals(candidate.getName())) {
                    bundled = candidate;
                } else {
                    celeritas.add(candidate);
                }
            }
        }
        // Without the bundled copy on the list nothing is dropped, stale copies included: one of them may be the only
        // Celeritas left, and the version gate judges whichever is loaded. With the bundled copy on the list, stale
        // copies are removed even when the bundled copy itself is left out, as on a dedicated server.
        boolean use = useBundled && bundled != null;
        List<File> dropExternal = new ArrayList<>();
        List<File> dropStale = new ArrayList<>();
        for (File jar : celeritas) {
            String name = jar.getName();
            if (bundled != null && name.startsWith(BUNDLED_PREFIX) && !name.equals(bundledName)
                && !namesInAnyContainedDeps.contains(name) && inExtractionFolder(jar)) {
                dropStale.add(jar);
            } else if (use) {
                dropExternal.add(jar);
            }
        }
        return new Plan(List.copyOf(dropExternal), List.copyOf(dropStale), !use && bundled != null);
    }

    /**
     * Whether a jar is in {@code mods/1.12.2}, the only folder Cleanroom extracts nested jars to. A copy with the
     * bundled prefix anywhere else was put there by the player, so it is treated as one they installed: left out of
     * the launch, never deleted.
     */
    private static boolean inExtractionFolder(File jar) {
        File parent = jar.getParentFile();
        return parent != null && "1.12.2".equals(parent.getName());
    }

    /**
     * Deletes the copies of Celeritas an earlier Demonica extracted, for the fallback in {@code MixinEarly} when this
     * hook never ran: the loader then put every Celeritas on the class path, and a stale copy whose name sorts before
     * the bundled one supplied this launch's Celeritas classes. Nothing can change that any more, but deleting the copy
     * makes the next launch right. {@code celeritasJars}: every Celeritas mod jar in {@code mods} and
     * {@code mods/1.12.2}. A copy is stale by the same rules as in {@link #plan}, so nothing is deleted unless the
     * bundled copy is among them, and a copy that any jar in those folders names in its {@code ContainedDeps} is kept.
     * Returns the names of the copies deleted, or to be deleted when the game exits; never throws.
     */
    public static List<String> deleteStaleCopies(List<File> celeritasJars, String bundledName) {
        try {
            if (!DELETE_STALE_BUNDLED || bundledName == null) {
                return List.of();
            }
            // The folders to read ContainedDeps from: mods/1.12.2 and mods, found from the copies themselves, since only
            // a copy in mods/1.12.2 can be stale.
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
            List<File> stale = plan(celeritasJars, bundledName, false, jar -> true, containedAnywhere).dropStale();
            for (File jar : stale) {
                delete(jar);
            }
            return names(stale);
        } catch (Throwable t) {
            try {
                logger().error("Could not delete the copies of Celeritas an earlier Demonica extracted", t);
            } catch (Throwable ignored) {
                // The launch must go on.
            }
            return List.of();
        }
    }

    /**
     * Brings FML's lists of jars in line with the decision: a jar on {@code ignored} and not on {@code reparseable} is
     * never identified as a mod. With the bundled copy in use, every other Celeritas jar is ignored. Otherwise the
     * bundled copy is: Demonica's {@code NonModDeps} attribute adds it to the class path, and FML would identify it as a
     * second {@code celeritas} next to the one the player installed. Changes the two lists in place, without adding a
     * name twice, and returns the names it ignored that were not ignored before; a second call changes nothing.
     */
    public static List<String> reconcile(List<String> reparseable, List<String> ignored, Collection<String> celeritasJarNames,
                                         String bundledName, boolean useBundled) {
        Set<String> toIgnore = new LinkedHashSet<>();
        if (useBundled) {
            for (String name : celeritasJarNames) {
                if (!name.equals(bundledName)) {
                    toIgnore.add(name);
                }
            }
        } else {
            toIgnore.add(bundledName);
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

    private static String report(String bundledName, String release, boolean client, boolean present, Plan plan,
                                 boolean deleteStale) {
        StringBuilder report = new StringBuilder();
        String bundled = bundledName + (release != null ? " (Celeritas-auto-build " + release + ")" : "");
        if (!client) {
            report.append("Dedicated server: Celeritas is client-only, so the Celeritas bundled with Demonica, ").append(bundled)
                .append(", is not loaded.");
        } else if (!present) {
            report.append("The Celeritas bundled with Demonica, ").append(bundled).append(", is not in mods/1.12.2 (it was not ")
                .append("extracted, or -Dfml.skipContainedDeps names it); any other Celeritas installed is left as it is, and ")
                .append("Demonica checks whether it is the build it was made for.");
        } else {
            report.append("Using the Celeritas bundled with Demonica, ").append(bundled).append('.');
            for (File external : plan.dropExternal()) {
                report.append(" Ignored the installed ").append(path(external))
                    .append(": Demonica brings its own Celeritas, and that jar can be removed.");
            }
        }
        if (!plan.dropStale().isEmpty()) {
            report.append(deleteStale ? " Removed " : " Left out ").append(plan.dropStale().size())
                .append(plan.dropStale().size() == 1 ? " copy" : " copies")
                .append(" of Celeritas an earlier Demonica extracted: ").append(String.join(", ", names(plan.dropStale())))
                .append('.');
        }
        return report.toString();
    }

    /** {@code mods/<name>} or {@code mods/1.12.2/<name>}, as the player finds it. */
    private static String path(File jar) {
        File parent = jar.getParentFile();
        if (parent == null) {
            return jar.getName();
        }
        File grandparent = parent.getParentFile();
        String prefix = "1.12.2".equals(parent.getName()) && grandparent != null ? grandparent.getName() + "/1.12.2/"
            : parent.getName() + "/";
        return prefix + jar.getName();
    }

    private static void delete(File stale) {
        try {
            Files.delete(stale.toPath());
        } catch (IOException | RuntimeException e) {
            // On Windows the jar may still be open (always, on the fallback path, where it is on the class path).
            try {
                stale.deleteOnExit();
            } catch (RuntimeException ignored) {
                // Then it stays, and is found stale again next time.
            }
            logger().info("Could not delete {} now ({}); it is deleted when the game exits", path(stale), e.toString());
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

    /** The main manifest attributes of the jar this class was loaded from; null if it was not loaded from a jar. */
    private static Attributes ownJarAttributes() throws IOException {
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
        if (jar == null || !jar.isFile()) {
            return null;
        }
        try (JarFile file = new JarFile(jar, false)) {
            Manifest manifest = file.getManifest();
            return manifest != null ? manifest.getMainAttributes() : null;
        }
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
