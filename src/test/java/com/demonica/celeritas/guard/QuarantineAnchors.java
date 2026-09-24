package com.demonica.celeritas.guard;

import com.demonica.celeritas.CeleritasJar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * The quarantine's anchors as the build extracts them ({@link AnchorExtractor}, from the main classes and the pinned
 * jar), and the list the build wrote into the mod's resources. Reads class bytes only.
 */
public final class QuarantineAnchors {
    private static List<Anchor> extracted;

    private QuarantineAnchors() {
    }

    /** The quarantine config's mixins, as binary names. */
    public static List<String> mixins() {
        try (InputStream in = resource(AnchorExtractor.QUARANTINE_CONFIG); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return AnchorExtractor.configMixins(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The mod's compiled classes: the root project's and the merged subprojects'. Not the whole test classpath, whose
     * test classes share packages with the mod (a {@code usesPackages} entry would pick them up).
     */
    public static ClassIndex mainClasses() {
        Path root = Path.of(System.getProperty("demonica.projectRoot", "."));
        return ClassIndex.ofDirectories(List.of(root.resolve("build/classes/java/main"), root.resolve("build/embedded-library-classes/main")));
    }

    public static ClassIndex pinnedJar() {
        try {
            return ClassIndex.ofJar(CeleritasJar.get().file().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The anchors of every quarantine mixin, extracted now. */
    public static synchronized List<Anchor> extracted() {
        if (extracted == null) {
            try {
                extracted = AnchorExtractor.extract(mixins(), mainClasses(), pinnedJar());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return extracted;
    }

    /** The anchor list the build put into the mod's resources. */
    public static AnchorFile.Contents generated() {
        try (InputStream in = resource(AnchorFile.RESOURCE); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return AnchorFile.read(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = QuarantineAnchors.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException(name + " is not on the test classpath");
        }
        return in;
    }
}
