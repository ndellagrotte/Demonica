package com.demonica.loading;

import net.minecraft.launchwrapper.Launch;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.ZipFile;

/**
 * What the coremod can learn about the installed mods before any of them is loaded. It looks for class files and
 * never loads a class, so no Celeritas or Actinium class is initialized, or cached untransformed, this early.
 *
 * <p>FML adds a coremod jar to the class path only when it reaches that jar, in file name order, so a mod whose jar
 * comes after Demonica's is not on the class path yet when Demonica's coremod starts. The mods folder is searched too.
 */
public final class Environment {
    private static final String CELERITAS_MARKER = "org/taumc/celeritas/CeleritasVintage.class";
    // S8TNLib's @Mod: a separate S8TNLib jar has it, and Demonica's jar, which merges only S8TNLib's GTNHLib classes,
    // never does.
    private static final String S8TNLIB_MOD_MARKER = "com/s8tnlib/S8TNLib.class";
    private static final String ACTINIUM_MARKER = "com/dhj/actinium/Actinium.class";

    private static Boolean actiniumPresent;

    private Environment() {
    }

    /** Whether the upstream Celeritas mod is installed. */
    public static boolean isCeleritasPresent() {
        return celeritasJar() != null || hasResource(CELERITAS_MARKER);
    }

    /**
     * A separate S8TNLib jar left in the mods folder from Demonica 0.3.0 to 0.5.0, which required it as a mod of its own.
     * Demonica carries GTNHLib itself again, so that jar only adds a second copy of the same classes. Null if there is
     * none.
     */
    public static @Nullable File staleS8tnlibJar() {
        return modsFolderJarContaining(S8TNLIB_MOD_MARKER);
    }

    /**
     * The jar the Celeritas mod is loaded from: the class path's, or else the one in the mods folder. Null if Celeritas
     * is not installed, or is not loaded from a jar (an exploded development build).
     */
    public static @Nullable File celeritasJar() {
        URL marker = resource(CELERITAS_MARKER);
        File jar = marker != null ? jarOf(marker) : null;
        return jar != null ? jar : modsFolderJarContaining(CELERITAS_MARKER);
    }

    /**
     * Whether Actinium is installed too. It carries its own renamed copy of Celeritas and patches the same classes
     * as Demonica, so the two cannot run together.
     */
    public static synchronized boolean isActiniumPresent() {
        if (actiniumPresent == null) {
            actiniumPresent = hasResource(ACTINIUM_MARKER) || modsFolderJarContaining(ACTINIUM_MARKER) != null;
        }
        return actiniumPresent;
    }

    private static boolean hasResource(String name) {
        return resource(name) != null;
    }

    private static @Nullable URL resource(String name) {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : Environment.class.getClassLoader();
        return loader.getResource(name);
    }

    /** The jar file of a {@code jar:file:...!/entry} URL, or null for any other URL. */
    static @Nullable File jarOf(URL url) {
        if (!"jar".equals(url.getProtocol())) {
            return null;
        }
        String path = url.getPath();
        int separator = path.indexOf("!/");
        if (separator < 0) {
            return null;
        }
        try {
            URI jar = new URI(path.substring(0, separator));
            return "file".equals(jar.getScheme()) ? new File(jar) : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable File modsFolderJarContaining(String entry) {
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        for (File dir : new File[] {new File(home, "mods"), new File(home, "mods/1.12.2")}) {
            File[] jars = dir.listFiles((parent, name) -> name.endsWith(".jar"));
            if (jars == null) {
                continue;
            }
            for (File jar : jars) {
                try (ZipFile zip = new ZipFile(jar)) {
                    if (zip.getEntry(entry) != null) {
                        return jar;
                    }
                } catch (IOException ignored) {
                    // Not a readable jar; FML will complain about it on its own.
                }
            }
        }
        return null;
    }
}
