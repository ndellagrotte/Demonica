package com.demonica.diagnostics;

import com.demonica.diagnostics.glsm.GlsmDrawLogSink;
import com.demonica.mixins.MixinEarly;
import com.gtnewhorizons.angelica.glsm.debug.GLSMDebug;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * The diagnostics jar's coremod. It registers the diagnostics mixins, but only when Demonica itself is active and is
 * the same version: the mixins and facades of the two jars are one build. Demonica's classes are read only from the
 * loader hooks, never from the constructor, and a missing Demonica makes this jar inert.
 */
@IFMLLoadingPlugin.Name("DemonicaDiagnostics")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class DiagnosticsCoremod implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaDiagnostics");
    private static final String MIXIN_CONFIG = "mixins.demonica.diagnostics.json";

    private static @Nullable Boolean usable;

    /** Whether Demonica is active and of this jar's version; decided once. */
    static synchronized boolean usable() {
        if (usable == null) {
            usable = decideUsable();
        }
        return usable;
    }

    private static boolean decideUsable() {
        boolean active;
        String demonicaVersion;
        try {
            active = MixinEarly.ACTIVE;
            demonicaVersion = jarVersion(MixinEarly.class);
        } catch (LinkageError missing) {
            LOGGER.error("Demonica is not installed; the diagnostics jar does nothing without it.");
            return false;
        }
        if (!active) {
            LOGGER.warn("Demonica is inactive; the diagnostics stay off.");
            return false;
        }
        String ownVersion = jarVersion(DiagnosticsCoremod.class);
        if (ownVersion != null && demonicaVersion != null && !ownVersion.equals(demonicaVersion)) {
            LOGGER.error("The diagnostics jar is version {} but Demonica is version {}; the diagnostics stay off. Install "
                + "Demonica-diagnostics-{}.jar.", ownVersion, demonicaVersion, demonicaVersion);
            return false;
        }
        return true;
    }

    /** Implementation-Version of the jar a class came from; null when it did not come from a jar (dev runs). */
    static @Nullable String jarVersion(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        Path jar = source != null && source.getLocation() != null ? jarPath(source.getLocation().toString()) : null;
        if (jar == null || !Files.isRegularFile(jar)) {
            return null;
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            return manifest != null ? manifest.getMainAttributes().getValue("Implementation-Version") : null;
        } catch (IOException e) {
            LOGGER.warn("Cannot read the manifest of {} ({})", jar, e.getMessage());
            return null;
        }
    }

    /** The jar file of a code source URL, file:/x.jar or jar:file:/x.jar!/...; null for anything else. */
    static @Nullable Path jarPath(String location) {
        String url = location;
        if (url.startsWith("jar:")) {
            int separator = url.indexOf("!/");
            url = url.substring("jar:".length(), separator >= 0 ? separator : url.length());
        }
        if (!url.startsWith("file:") || !url.endsWith(".jar")) {
            return null;
        }
        try {
            return Path.of(new URI(url));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // Now rather than with the rest of the diagnostics: the GL redirector reports unmapped GL calls while it
        // transforms classes, and most are transformed before the game initializes GL.
        if (usable()) {
            GLSMDebug.install(GlsmDrawLogSink.INSTANCE);
        }
    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return usable() ? List.of(MIXIN_CONFIG) : List.of();
    }
}
