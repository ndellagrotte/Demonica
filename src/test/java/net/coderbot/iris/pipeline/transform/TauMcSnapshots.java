package net.coderbot.iris.pipeline.transform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * TauMC's answers, frozen: the oracle of the tests that compared glsl-transformer against TauMC's
 * glsl-transformation-lib until Step 11 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md removed that library. Each
 * test method has one file, {@code src/test/resources/<directory>/<method>.txt}, of entries: a line
 * {@value #SEPARATOR}{@code <key>}, then the value's lines. The values were recorded from TauMC's library by the tests'
 * record mode (system property {@value #RECORD_PROPERTY}) at Step 11, while it was still on the class path, and the
 * same tests then compared TauMC's live answers with the files before the library went.
 *
 * <p>A value is TauMC's printed program (token-spaced, compare it with {@link GlslTokens}), a query's answer as text,
 * or {@code !throws <exception class>: <message>} when TauMC threw.</p>
 */
public final class TauMcSnapshots {
    public static final String SEPARATOR = "==== ";
    public static final String THROWS = "!throws ";
    /** Set to {@code true} to record (Step 11 only; the library is gone since). */
    public static final String RECORD_PROPERTY = "demonica.taumc.snapshots.record";

    private final String directory;
    private final Map<String, Map<String, String>> loaded = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<String, String>> recorded = new ConcurrentHashMap<>();

    public TauMcSnapshots(String directory) {
        this.directory = directory;
    }

    public static boolean recording() {
        return Boolean.getBoolean(RECORD_PROPERTY);
    }

    /** TauMC's answer {@code key} of test method {@code file}; fails when there is none. */
    public String get(String file, String key) {
        final Map<String, String> entries = loaded.computeIfAbsent(file, this::load);
        final String value = entries.get(key);
        if (value == null) {
            fail("no TauMC snapshot '" + key + "' in " + directory + "/" + file + ".txt");
        }
        return value;
    }

    /** Whether TauMC threw for {@code key}, and the exception it threw. */
    public static boolean threw(String value) {
        return value.startsWith(THROWS);
    }

    /** The value that stands for an exception TauMC threw. */
    public static String thrown(Throwable exception) {
        final String message = exception.getMessage();
        return THROWS + exception.getClass().getName() + (message == null ? "" : ": " + message.replace('\n', ' ').replace('\r', ' '));
    }

    public void record(String file, String key, String value) {
        if (value.contains("\r")) {
            fail("a TauMC snapshot value must not contain a carriage return: " + file + " / " + key);
        }
        if (key.contains("\n")) {
            fail("a snapshot key must be one line: " + key);
        }
        final String previous = recorded.computeIfAbsent(file, f -> new TreeMap<>()).putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) {
            fail("two different values for the TauMC snapshot " + file + " / " + key);
        }
    }

    /** Writes the recorded files under {@code <projectRoot>/src/test/resources/<directory>/}, entries sorted by key. */
    public void writeRecorded() {
        final Path root = Path.of(System.getProperty("demonica.projectRoot", "."), "src", "test", "resources", directory);
        try {
            Files.createDirectories(root);
            for (Map.Entry<String, TreeMap<String, String>> file : recorded.entrySet()) {
                final StringBuilder text = new StringBuilder();
                file.getValue().forEach((key, value) -> text.append(SEPARATOR).append(key).append('\n').append(value).append('\n'));
                Files.writeString(root.resolve(file.getKey() + ".txt"), text.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> load(String file) {
        final String path = "/" + directory + "/" + file + ".txt";
        final String text;
        try (InputStream in = TauMcSnapshots.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing TauMC snapshot file " + path);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final Map<String, String> entries = new LinkedHashMap<>();
        String key = null;
        StringBuilder value = null;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(SEPARATOR)) {
                if (key != null) {
                    entries.put(key, strip(value.toString()));
                }
                key = line.substring(SEPARATOR.length());
                value = new StringBuilder();
            } else if (value != null) {
                value.append(line).append('\n');
            }
        }
        if (key != null) {
            // split("\n", -1) gave one more empty line after the file's last '\n'.
            entries.put(key, strip(strip(value.toString())));
        }
        return entries;
    }

    // Every value was written with one '\n' after it.
    private static String strip(String text) {
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }
}
