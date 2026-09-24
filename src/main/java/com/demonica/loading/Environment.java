package com.demonica.loading;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * What the coremod can learn about the installed mods before any of them is loaded. It looks for class files and
 * never loads a class, so no Celeritas or Actinium class is initialized, or cached untransformed, this early.
 */
public final class Environment {
    private static final String CELERITAS_MARKER = "org/taumc/celeritas/CeleritasVintage.class";
    private static final String ACTINIUM_MARKER = "com/dhj/actinium/Actinium.class";

    private static Boolean actiniumPresent;

    private Environment() {
    }

    /** Whether the upstream Celeritas mod is installed. */
    public static boolean isCeleritasPresent() {
        return hasResource(CELERITAS_MARKER);
    }

    /**
     * Whether Actinium is installed too. It carries its own renamed copy of Celeritas and patches the same classes
     * as Demonica, so the two cannot run together. FML adds a coremod jar to the class path only when it reaches
     * that jar, so the mods folder is searched as well.
     */
    public static synchronized boolean isActiniumPresent() {
        if (actiniumPresent == null) {
            actiniumPresent = hasResource(ACTINIUM_MARKER) || modsFolderContains(ACTINIUM_MARKER);
        }
        return actiniumPresent;
    }

    private static boolean hasResource(String name) {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : Environment.class.getClassLoader();
        return loader.getResource(name) != null;
    }

    private static boolean modsFolderContains(String entry) {
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        for (File dir : new File[] {new File(home, "mods"), new File(home, "mods/1.12.2")}) {
            File[] jars = dir.listFiles((parent, name) -> name.endsWith(".jar"));
            if (jars == null) {
                continue;
            }
            for (File jar : jars) {
                try (ZipFile zip = new ZipFile(jar)) {
                    if (zip.getEntry(entry) != null) {
                        return true;
                    }
                } catch (IOException ignored) {
                    // Not a readable jar; FML will complain about it on its own.
                }
            }
        }
        return false;
    }
}
