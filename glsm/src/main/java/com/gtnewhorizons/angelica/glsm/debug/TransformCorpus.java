package com.gtnewhorizons.angelica.glsm.debug;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records shader transform calls as a replayable corpus (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, 3.5).
 *
 * <p>Inert unless the system property {@value #PROPERTY} names a directory: then every recorded call becomes one
 * directory {@code <dir>/<seq>-<kind>-<hash8>/} holding {@code case.properties} and the case's {@code in.*} and
 * {@code out.*} files. {@code kind} is the Iris patch kind, or {@code COMPAT} for GLSM's mod-shader path;
 * {@code hash8} is the start of the SHA-256 of the case's inputs, and a case whose inputs were already recorded (in this
 * run or an earlier one into the same directory) is skipped.</p>
 *
 * <p>Iris's {@code TransformPatcher} records through {@code TransformCorpusRecorder} in the shader project, which cannot
 * be reached from here; {@code CompatShaderTransformer} records through {@link #recordCompat}. Recording never throws
 * into the caller: the first failure is logged, later ones are counted silently.</p>
 */
public final class TransformCorpus {
    /** The system property that names the corpus directory. */
    public static final String PROPERTY = "demonica.glsl.corpus";
    /** The file that describes a case. */
    public static final String CASE_FILE = "case.properties";

    private static final Logger LOGGER = LogManager.getLogger("TransformCorpus");
    private static final Path DIR = resolveDir(System.getProperty(PROPERTY));
    private static final Pattern CASE_DIR = Pattern.compile("(\\d+)-([A-Z_]+)-([0-9a-f]{8})");
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicInteger FAILURES = new AtomicInteger();

    private TransformCorpus() {
    }

    /** Whether recording is on. A constant per JVM: the only cost of the hooks when the property is unset. */
    public static boolean isEnabled() {
        return DIR != null;
    }

    private static Path resolveDir(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Paths.get(value.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            LOGGER.error("[TransformCorpus] not recording: -D" + PROPERTY + "=" + value + " is not a usable path", e);
            return null;
        }
    }

    /** One call to record: ordered input properties and files, then result properties and output files. */
    public static final class Case {
        final String domain;
        final String kind;
        final Map<String, String> inputProperties = new LinkedHashMap<>();
        final Map<String, String> inputFiles = new LinkedHashMap<>();
        final Map<String, String> resultProperties = new LinkedHashMap<>();
        final Map<String, String> outputFiles = new LinkedHashMap<>();

        public Case(String domain, String kind) {
            this.domain = domain;
            this.kind = kind;
        }

        /** A property the transform's result depends on; part of the case's hash. */
        public Case input(String key, Object value) {
            inputProperties.put(key, String.valueOf(value));
            return this;
        }

        /** An input source file ({@code in.<stage>.glsl}); part of the case's hash. Null sources are left out. */
        public Case inputFile(String name, String content) {
            if (content != null) {
                inputFiles.put(name, content);
            }
            return this;
        }

        /** A property describing this call's result or provenance; not part of the hash. */
        public Case result(String key, Object value) {
            resultProperties.put(key, String.valueOf(value));
            return this;
        }

        /** An output file ({@code out.<engine>.<stage>.glsl}). Null outputs are left out. */
        public Case outputFile(String name, String content) {
            if (content != null) {
                outputFiles.put(name, content);
            }
            return this;
        }
    }

    /**
     * Counts a failure to record; the first one is logged with its stack trace. For callers that build a case and
     * catch their own failure, so that recording never throws into the transform.
     */
    public static void reportFailure(Throwable e) {
        FAILURES.incrementAndGet();
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            LOGGER.error("[TransformCorpus] recording failed; the transform is unaffected, and later failures are"
                + " only counted", e);
        }
    }

    /** Writes {@code c} unless its inputs were recorded already. Never throws. */
    public static void record(Case c) {
        if (DIR == null) {
            return;
        }
        try {
            Writer.INSTANCE.write(c);
        } catch (Exception | LinkageError e) {
            reportFailure(e);
        }
    }

    /**
     * Records one {@code CompatShaderTransformer.transform} call: {@code source} as the method received it (after
     * GLStateManager's reserved-word renaming), and {@code output} as it returned it.
     *
     * @param fallback whether the AST transform threw and the output is the version fix-up only
     */
    public static void recordCompat(String source, boolean isFragment, String output, boolean fallback, String engine,
                                    long elapsedNanos) {
        if (DIR == null) {
            return;
        }
        try {
            // CompatShaderTransformer raises the version to at least this (330 when there is no backend).
            final int minGlslVersion = BackendManager.RENDER_BACKEND != null ? BackendManager.RENDER_BACKEND.getMinGLSLVersion() : 330;
            final Case c = new Case("compat", "COMPAT")
                .input("isFragment", isFragment)
                .input("minGlslVersion", minGlslVersion)
                .inputFile("in.glsl", source)
                .result("engine", engine)
                .result("outcome", fallback ? "fallback" : "ok")
                .result("transformMs", elapsedNanos / 1_000_000.0)
                .outputFile("out." + engine + ".glsl", output);
            record(c);
        } catch (Exception | LinkageError e) {
            reportFailure(e);
        }
    }

    /** Reads a {@code case.properties} written by this class: {@code key=value} lines, {@code #} comments. */
    public static Map<String, String> readCaseProperties(Path file) throws IOException {
        final Map<String, String> properties = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            final int separator = line.indexOf('=');
            if (separator < 0) {
                throw new IOException(file + ": not a key=value line: " + line);
            }
            properties.put(line.substring(0, separator), unescape(line.substring(separator + 1)));
        }
        return properties;
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String unescape(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        final StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                final char next = value.charAt(++i);
                out.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next);
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /** Created on the first recorded case, so an unset property never touches the file system or starts a process. */
    private static final class Writer {
        static final Writer INSTANCE = new Writer();

        private final Set<String> seenHashes = Collections.synchronizedSet(new HashSet<>());
        private final AtomicInteger sequence;
        private final AtomicInteger written = new AtomicInteger();
        private final Map<String, String> provenance;

        private Writer() {
            int maxSequence = 0;
            try {
                Files.createDirectories(DIR);
                try (DirectoryStream<Path> children = Files.newDirectoryStream(DIR)) {
                    for (Path child : children) {
                        final Matcher m = CASE_DIR.matcher(child.getFileName().toString());
                        if (m.matches()) {
                            maxSequence = Math.max(maxSequence, Integer.parseInt(m.group(1)));
                            seenHashes.add(m.group(3));
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                reportFailure(e);
            }
            this.sequence = new AtomicInteger(maxSequence);
            this.provenance = provenance();
            LOGGER.info("[TransformCorpus] recording shader transforms to {} ({} earlier cases; git {})", DIR,
                seenHashes.size(), provenance.get("gitHead"));
        }

        void write(Case c) throws IOException {
            final String hash = hash(c);
            final String hash8 = hash.substring(0, 8);
            if (!seenHashes.add(hash8)) {
                return;
            }
            final String name = String.format("%05d-%s-%s", sequence.incrementAndGet(), c.kind, hash8);
            final Path caseDir = DIR.resolve(name);
            Files.createDirectories(caseDir);
            for (Map.Entry<String, String> file : c.inputFiles.entrySet()) {
                Files.writeString(caseDir.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
            }
            for (Map.Entry<String, String> file : c.outputFiles.entrySet()) {
                Files.writeString(caseDir.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
            }

            final StringBuilder properties = new StringBuilder();
            properties.append("# Demonica transform corpus case (docs/glsl-transformer_adoption/ADOPTION_PLAN.md, 3.5)\n");
            appendProperty(properties, "domain", c.domain);
            appendProperty(properties, "patch", c.kind);
            appendProperty(properties, "hash", hash);
            c.inputProperties.forEach((k, v) -> appendProperty(properties, k, v));
            appendProperty(properties, "files.in", String.join(",", c.inputFiles.keySet()));
            appendProperty(properties, "files.out", String.join(",", c.outputFiles.keySet()));
            c.resultProperties.forEach((k, v) -> appendProperty(properties, k, v));
            appendProperty(properties, "thread", Thread.currentThread().getName());
            appendProperty(properties, "recordedAt", Instant.now().toString());
            provenance.forEach((k, v) -> appendProperty(properties, k, v));
            appendProperty(properties, "recorderFailures", FAILURES.get());
            Files.writeString(caseDir.resolve(CASE_FILE), properties.toString(), StandardCharsets.UTF_8);
            written.incrementAndGet();
        }

        private static void appendProperty(StringBuilder out, String key, Object value) {
            out.append(key).append('=').append(escape(String.valueOf(value))).append('\n');
        }

        private static String hash(Case c) {
            final MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            update(digest, c.domain);
            update(digest, c.kind);
            c.inputProperties.forEach((k, v) -> {
                update(digest, k);
                update(digest, v);
            });
            c.inputFiles.forEach((k, v) -> {
                update(digest, k);
                update(digest, v);
            });
            final StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }

        private static void update(MessageDigest digest, String value) {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) ':');
            digest.update(bytes);
        }

        /** Provenance shared by every case of this run: the build's git state and the OpenGL context. */
        private static Map<String, String> provenance() {
            final Map<String, String> result = new LinkedHashMap<>();
            final String head = git("rev-parse", "--short", "HEAD");
            result.put("gitHead", head == null || head.isEmpty() ? "unknown" : head);
            // Tracked files only: an untracked file does not change the build.
            final String status = git("status", "--porcelain", "--untracked-files=no");
            result.put("gitDirty", status == null ? "unknown" : String.valueOf(!status.isEmpty()));
            final String profileProperty = System.getProperty("demonica.openglProfile");
            result.put("openglProfile.property", profileProperty == null ? "unset" : profileProperty);
            result.put("openglProfile.context", RenderSystem.getContextProfile());
            return result;
        }

        /** The command's trimmed output, or null when git is missing, fails or takes longer than ten seconds. */
        private static String git(String... args) {
            try {
                final List<String> command = new ArrayList<>();
                command.add("git");
                command.addAll(List.of(args));
                final Process process = new ProcessBuilder(command)
                    .directory(DIR.toFile())
                    .redirectErrorStream(true)
                    .start();
                final String out;
                try (InputStream in = process.getInputStream()) {
                    out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
                if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    process.destroyForcibly();
                    return null;
                }
                return out;
            } catch (IOException | RuntimeException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
