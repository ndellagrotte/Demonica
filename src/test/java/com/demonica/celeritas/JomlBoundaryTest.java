package com.demonica.celeritas;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Celeritas's mod jar relocates JOML to {@code org.embeddedt.embeddium.impl.shadow.joml}, while Demonica computes with
 * Cleanroom's {@code org.joml}. Code that names the relocated package links only against that one Celeritas build,
 * so it stays confined to the classes below, which sit directly on the seam.
 */
class JomlBoundaryTest {
    private static final String RELOCATED_JOML = "org/embeddedt/embeddium/impl/shadow/joml/";

    /** The only classes that may reference the relocated JOML. */
    private static final Set<String> ALLOWED = Set.of(
        // The conversion helper everything else goes through.
        "com/demonica/celeritas/CeleritasJoml",
        // Implements Celeritas's ChunkShaderInterface, whose matrix setters take the relocated types.
        "net/coderbot/iris/celeritas/IrisCeleritasChunkShaderInterface"
    );

    @Test
    void onlyTheSeamReferencesRelocatedJoml() throws IOException, URISyntaxException {
        Set<String> offenders = new TreeSet<>();
        Set<String> referencing = new TreeSet<>();
        // The shader tree (where the seam classes live) and the mod's own classes are compiled separately.
        for (Path root : new TreeSet<>(List.of(classesRoot("com/demonica/celeritas/CeleritasJoml.class"),
                classesRoot("com/demonica/Demonica.class")))) {
            try (Stream<Path> files = Files.walk(root)) {
                List<Path> classes = files.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
                for (Path file : classes) {
                    String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                    if (!bytes.contains(RELOCATED_JOML)) {
                        continue;
                    }
                    String name = root.relativize(file).toString().replace('\\', '/');
                    name = name.substring(0, name.length() - ".class".length());
                    // Nested and synthetic classes share their outer class's allowance.
                    String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
                    referencing.add(outer);
                    if (!ALLOWED.contains(outer)) {
                        offenders.add(name);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "these classes reference Celeritas's relocated JOML; convert through "
            + "com.demonica.celeritas.CeleritasJoml instead:\n  " + String.join("\n  ", offenders));
        assertEquals(new TreeSet<>(ALLOWED), referencing, "an allow-listed class no longer references the relocated JOML; drop it from the list");
    }

    /** The output directory that holds {@code classFile}. */
    private static Path classesRoot(String classFile) throws URISyntaxException {
        URL marker = JomlBoundaryTest.class.getClassLoader().getResource(classFile);
        if (marker == null || !"file".equals(marker.getProtocol())) {
            throw new IllegalStateException("main classes are not on the test classpath as a directory: " + marker);
        }
        Path root = Paths.get(marker.toURI());
        for (int depth = classFile.split("/").length; depth > 0; depth--) {
            root = root.getParent();
        }
        return root;
    }
}
