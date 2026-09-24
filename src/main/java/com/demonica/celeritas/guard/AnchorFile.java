package com.demonica.celeritas.guard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code META-INF/demonica/celeritas-anchors}: the Celeritas build the mod jar was made for (its upstream commit,
 * version and accepted SHA-256s) and every anchor of the quarantine's patches. The build writes it
 * ({@link AnchorExtractor}); {@link QuarantineGuard} reads it at startup. One record per line, fields separated by tabs:
 * <pre>
 * celeritas  upstream-commit  version
 * pin        sha256
 * anchor     mixin  group  patches  kind  owner  member  target
 * </pre>
 * An empty member or target is written as {@code -}. Lines starting with {@code #} are comments.
 */
public final class AnchorFile {
    public static final String RESOURCE = "META-INF/demonica/celeritas-anchors";

    private static final String EMPTY = "-";

    /** The file's records. {@code pins} are lower-case SHA-256s. */
    public record Contents(String upstreamCommit, String version, Set<String> pins, List<Anchor> anchors) {
    }

    private AnchorFile() {
    }

    public static Contents read(Reader reader) throws IOException {
        BufferedReader lines = new BufferedReader(reader);
        String upstreamCommit = null;
        String version = null;
        Set<String> pins = new LinkedHashSet<>();
        List<Anchor> anchors = new ArrayList<>();
        int number = 0;
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            try {
                switch (fields[0]) {
                    case "celeritas" -> {
                        expect(fields, 3);
                        upstreamCommit = fields[1];
                        version = fields[2];
                    }
                    case "pin" -> {
                        expect(fields, 2);
                        pins.add(fields[1].toLowerCase(Locale.ROOT));
                    }
                    case "anchor" -> {
                        expect(fields, 8);
                        anchors.add(new Anchor(fields[1], PatchGroup.valueOf(fields[2]), fields[3], Anchor.Kind.valueOf(fields[4]),
                            fields[5], unempty(fields[6]), unempty(fields[7])));
                    }
                    default -> throw new IllegalArgumentException("unknown record " + fields[0]);
                }
            } catch (IllegalArgumentException e) {
                throw new IOException(RESOURCE + " line " + number + ": " + e.getMessage(), e);
            }
        }
        if (upstreamCommit == null || pins.isEmpty() || anchors.isEmpty()) {
            throw new IOException(RESOURCE + " names no Celeritas build, no pin or no anchor");
        }
        return new Contents(upstreamCommit, version, pins, anchors);
    }

    public static void write(Writer writer, Contents contents) throws IOException {
        writer.write("# The Celeritas build this jar was made for, and every anchor of the patches in mixins.demonica.celeritas.json.\n");
        writer.write("# Generated from the compiled quarantine mixins by com.demonica.celeritas.guard.AnchorExtractor; do not edit.\n");
        writer.write("# QuarantineGuard checks the anchors when the installed Celeritas jar is not one of the pins.\n");
        writer.write(String.join("\t", "celeritas", contents.upstreamCommit(), contents.version()) + "\n");
        for (String pin : contents.pins()) {
            writer.write("pin\t" + pin + "\n");
        }
        for (Anchor anchor : contents.anchors()) {
            writer.write(String.join("\t", "anchor", anchor.mixin(), anchor.group().name(), anchor.patches(), anchor.kind().name(),
                anchor.owner(), orEmpty(anchor.member()), orEmpty(anchor.target())) + "\n");
        }
    }

    private static void expect(String[] fields, int count) {
        if (fields.length != count) {
            throw new IllegalArgumentException(fields[0] + " has " + fields.length + " fields, expected " + count);
        }
    }

    private static String unempty(String field) {
        return EMPTY.equals(field) ? "" : field;
    }

    private static String orEmpty(String field) {
        return field.isEmpty() ? EMPTY : field;
    }
}
