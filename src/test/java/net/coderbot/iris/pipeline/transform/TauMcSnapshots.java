package net.coderbot.iris.pipeline.transform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * TauMC's answers, frozen: the oracle of the tests that compared glsl-transformer against TauMC's
 * glsl-transformation-lib until Step 11 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md removed that library. Each
 * test method has one file, {@code src/test/resources/<directory>/<method>.txt}, of entries: a line
 * {@value #SEPARATOR}{@code <key>}, then the value's lines. The values were recorded from TauMC's library at Step 11 by
 * the record mode of {@code ShaderAstParityTest} and {@code AstShaderTransformerTest} (commit "glsl-transformer: S11
 * freeze TauMC's answers as test snapshots"), while the library was still on the class path, and those tests then
 * compared TauMC's live answers with the files before the library went. They cannot be re-recorded.
 *
 * <p>A value is TauMC's printed program (token-spaced, compare it with {@link GlslTokens}), a query's answer as text,
 * or {@code !throws <exception class>: <message>} when TauMC threw.</p>
 */
final class TauMcSnapshots {
    public static final String SEPARATOR = "==== ";
    public static final String THROWS = "!throws ";

    private final String directory;
    private final Map<String, Map<String, String>> loaded = new ConcurrentHashMap<>();

    public TauMcSnapshots(String directory) {
        this.directory = directory;
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

    /** Whether the value stands for an exception TauMC threw. */
    public static boolean threw(String value) {
        return value.startsWith(THROWS);
    }

    /** A new exception of the recorded type, with the recorded message. */
    public static RuntimeException exception(String value) {
        final String description = value.substring(THROWS.length());
        final int colon = description.indexOf(": ");
        final String type = colon < 0 ? description : description.substring(0, colon);
        final String message = colon < 0 ? null : description.substring(colon + 2);
        try {
            return (RuntimeException) Class.forName(type).getConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("cannot rebuild TauMC's exception " + value, e);
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
