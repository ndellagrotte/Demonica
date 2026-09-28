package com.demonica.celeritas.guard;

import com.demonica.loading.Environment;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The version gate on the installed Celeritas (docs/celeritas/LEDGER.md, "The guard"). Every upstream dev build calls
 * itself {@code 2.4.0-dev}, so the jar's SHA-256 is its only identity. If it is one of the builds this Demonica was made
 * for ({@link #RESOURCE}), every quarantine patch applies. Otherwise shaders are off, with the reason on the shader pack
 * screen, and only the {@link PatchGroup#BASE} group applies, whose injectors each stand alone. The decision is made
 * once, before Mixin applies any quarantine mixin; this never loads a Celeritas class.
 *
 * <p>A development workspace runs Unimined's remap of the pin, which no pin matches; the build records its hash as
 * {@code dev_sha256}, trusted only in a deobfuscated environment.
 */
public final class QuarantineGuard {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaQuarantine");
    public static final String RESOURCE = "META-INF/demonica/celeritas-pin";
    /** {@code -Ddemonica.celeritas.pinsOnly=true}: ignore {@code dev_sha256}, to see the rejection path in a dev client. */
    public static final String PINS_ONLY_PROPERTY = "demonica.celeritas.pinsOnly";

    /** The Celeritas build this Demonica was made for, as {@code generateCeleritasPin} writes it. */
    record Pin(String upstreamCommit, String version, Set<String> accepted, @Nullable String devSha256) {
        /** Reads the pin; throws if a key is missing or no SHA-256 is pinned. */
        static Pin read(InputStream in) throws IOException {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> accepted = Arrays.stream(required(properties, "celeritas_sha256").split(","))
                .map(sha -> sha.trim().toLowerCase(Locale.ROOT)).filter(sha -> !sha.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (accepted.isEmpty()) {
                throw new IOException("celeritas_sha256 names no SHA-256");
            }
            String dev = properties.getProperty("dev_sha256");
            return new Pin(required(properties, "celeritas_sha"), required(properties, "celeritas_version"), Set.copyOf(accepted),
                dev != null && !dev.isBlank() ? dev.trim().toLowerCase(Locale.ROOT) : null);
        }

        private static String required(Properties properties, String key) throws IOException {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IOException(key + " is missing");
            }
            return value.trim();
        }
    }

    /** What the gate decided. */
    public static final class Verdict {
        private final boolean accepted;
        private final @Nullable String shaderReason;

        Verdict(boolean accepted, @Nullable String shaderReason) {
            this.accepted = accepted;
            this.shaderReason = shaderReason;
        }

        /** Whether the installed Celeritas is a build this Demonica was made for. */
        public boolean accepted() {
            return this.accepted;
        }

        /** Whether shader packs may be used. */
        public boolean shadersAllowed() {
            return this.accepted;
        }

        /** Why shaders are off, for the shader pack screen; null when the jar is accepted. */
        public @Nullable String shaderReason() {
            return this.shaderReason;
        }

        /**
         * Whether Mixin may apply the quarantine mixin: every one when the jar is accepted; otherwise only a mixin of
         * the {@link PatchGroup#BASE} group (one whose group cannot be read does not apply).
         */
        public boolean shouldApply(String mixinClassName, GroupLookup groups) {
            return this.accepted || groups.groupOf(mixinClassName) == PatchGroup.BASE;
        }
    }

    /** The group of a quarantine mixin, read from its {@link Patch}; null if it cannot be read. */
    @FunctionalInterface
    public interface GroupLookup {
        @Nullable PatchGroup groupOf(String mixinClassName);
    }

    private static volatile @Nullable Verdict current;

    private QuarantineGuard() {
    }

    /**
     * The decision for this game, made (and logged) on first use: by the quarantine's config plugin before Mixin
     * applies any of its mixins, or by whatever asks first. Never throws.
     */
    public static Verdict current() {
        Verdict verdict = current;
        if (verdict == null) {
            synchronized (QuarantineGuard.class) {
                verdict = current;
                if (verdict == null) {
                    verdict = current = evaluate();
                }
            }
        }
        return verdict;
    }

    /** Whether this is a deobfuscated (development) environment. */
    public static boolean isDevelopment() {
        Object deobfuscated = Launch.blackboard != null ? Launch.blackboard.get("fml.deobfuscatedEnvironment") : null;
        return Boolean.TRUE.equals(deobfuscated);
    }

    private static Verdict evaluate() {
        if (Launch.classLoader == null) {
            // Unit tests, or any other code outside a launched game: there is no installed Celeritas to check.
            return new Verdict(true, null);
        }
        try {
            Pin pin = null;
            String pinProblem = null;
            try (InputStream in = Launch.classLoader.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    pinProblem = RESOURCE + " is missing from the Demonica jar";
                } else {
                    pin = Pin.read(in);
                }
            } catch (IOException | RuntimeException e) {
                pinProblem = RESOURCE + " could not be read (" + e.getMessage() + ")";
            }
            File jar = Environment.celeritasJar();
            String sha256 = null;
            if (jar != null) {
                try {
                    sha256 = sha256(jar);
                } catch (IOException e) {
                    LOGGER.warn("Could not read {} to compare it with the pin", jar, e);
                }
            }
            return decide(pin, pinProblem, jar != null ? jar.getName() : null, sha256,
                isDevelopment() && !Boolean.getBoolean(PINS_ONLY_PROPERTY));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("The Celeritas version gate failed; shaders are off, and only the fog patch (S15) applies", e);
            return new Verdict(false, "Shaders are off: Demonica could not check the installed Celeritas (" + e + ").");
        }
    }

    /**
     * The decision from its inputs, logged. {@code development}: whether the workspace's remap ({@link Pin#devSha256})
     * counts as a pin.
     */
    static Verdict decide(@Nullable Pin pin, @Nullable String pinProblem, @Nullable String jarName, @Nullable String jarSha256,
                          boolean development) {
        String jar = jarName != null ? jarName : "Celeritas (not loaded from a jar)";
        if (pin == null) {
            String problem = pinProblem != null ? pinProblem : RESOURCE + " is missing";
            LOGGER.error("Cannot check the installed Celeritas: {}. Shaders are off, and only the fog patch (S15) applies.", problem);
            return new Verdict(false, "Shaders are off: Demonica could not check the installed Celeritas (" + problem + ").");
        }
        String upstream = abbreviate(pin.upstreamCommit());
        String sha = jarSha256 != null ? jarSha256.toLowerCase(Locale.ROOT) : null;
        if (sha != null && pin.accepted().contains(sha)) {
            LOGGER.info("{} is the Celeritas build this Demonica was made for (upstream {}, SHA-256 {})", jar, upstream, abbreviate(sha));
            return new Verdict(true, null);
        }
        if (sha != null && development && sha.equals(pin.devSha256())) {
            LOGGER.info("{} is the build this workspace was built against (dev remap SHA-256 {}, of the pin: upstream {})", jar,
                abbreviate(sha), upstream);
            return new Verdict(true, null);
        }
        String install = " Install the Celeritas build of upstream commit " + upstream + ".";
        if (jarName == null) {
            LOGGER.error("Cannot find the Celeritas jar to compare with the build this Demonica was made for (upstream commit {}, "
                + "SHA-256 {}). Shaders are off, and only the fog patch (S15) applies.", pin.upstreamCommit(), String.join(" or ", pin.accepted()));
            return new Verdict(false, "Shaders are off: Demonica could not find the Celeritas jar to check it." + install);
        }
        if (sha == null) {
            LOGGER.error("Cannot read {} to compare it with the build this Demonica was made for (upstream commit {}). Shaders "
                + "are off, and only the fog patch (S15) applies.", jar, pin.upstreamCommit());
            return new Verdict(false, "Shaders are off: Demonica could not read " + jar + " to check it." + install);
        }
        LOGGER.error("{} is not the Celeritas build this Demonica was made for: expected the build of upstream commit {} (SHA-256 {}), "
            + "found SHA-256 {}. Shaders are off, and only the fog patch (S15) applies.", jar, pin.upstreamCommit(),
            String.join(" or ", pin.accepted()), sha);
        return new Verdict(false, "Shaders are off: " + jar + " is not the Celeritas build this Demonica was made for." + install);
    }

    static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new DigestInputStream(Files.newInputStream(file.toPath()), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String abbreviate(String hash) {
        return hash.length() > 8 ? hash.substring(0, 8) : hash;
    }
}
