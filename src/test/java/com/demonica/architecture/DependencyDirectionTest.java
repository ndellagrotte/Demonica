package com.demonica.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces the module dependency direction at the bytecode level.
 *
 * <p>Subprojects (shader/glsm) must never reference the root project's
 * {@code com.demonica} implementation classes. Gradle already enforces this at compile
 * time via each subproject's classpath; this test backstops that constraint on the
 * compiled output, so a build-configuration regression cannot silently re-introduce a
 * reverse dependency. The shader tree's own {@code com.demonica} packages (the Celeritas
 * seam types, {@code CeleritasJoml}, the GL timer queries) are not root classes.
 */
class DependencyDirectionTest {

    private static final String ROOT_PACKAGE_BINARY = "com/demonica/";


    private static final List<String> SUBPROJECTS = List.of(
        "shader",
        "glsm"
    );

    @Test
    void subprojectsDoNotReferenceRootProject() throws IOException {
        final String projectRoot = System.getProperty("demonica.projectRoot");
        if (projectRoot == null) {
            fail("Missing system property demonica.projectRoot - run tests via the root Gradle project");
        }
        final Map<String, Path> classesDirs = new LinkedHashMap<>();
        for (String subproject : SUBPROJECTS) {
            final Path classesDir = Path.of(projectRoot, subproject, "build", "classes", "java", "main");
            assertTrue(Files.isDirectory(classesDir),
                "Missing compiled classes for " + subproject + " - run the root project test task so subprojects are built first");
            classesDirs.put(subproject, classesDir);
        }
        final Set<String> subprojectClasses = subprojectClassNames(classesDirs.values());
        for (Map.Entry<String, Path> entry : classesDirs.entrySet()) {
            try (Stream<Path> classes = Files.walk(entry.getValue())) {
                classes.filter(path -> path.getFileName().toString().endsWith(".class"))
                    .forEach(path -> assertClassHasNoRootReference(entry.getKey(), path, subprojectClasses));
            }
        }
    }

    /** The binary names of every class the subprojects compile, which may use {@code com/demonica} themselves. */
    private static Set<String> subprojectClassNames(Iterable<Path> classesDirs) throws IOException {
        final Set<String> names = new HashSet<>();
        for (Path classesDir : classesDirs) {
            try (Stream<Path> classes = Files.walk(classesDir)) {
                classes.filter(path -> path.getFileName().toString().endsWith(".class")).forEach(path -> {
                    String name = classesDir.relativize(path).toString().replace('\\', '/');
                    names.add(name.substring(0, name.length() - ".class".length()));
                });
            }
        }
        return names;
    }

    private static void assertClassHasNoRootReference(String subproject, Path classFile, Set<String> subprojectClasses) {
        try {
            final byte[] bytes = Files.readAllBytes(classFile);
            final String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            int index = content.indexOf(ROOT_PACKAGE_BINARY);
            while (index >= 0) {
                int end = index;
                while (end < content.length() && isNameChar(content.charAt(end))) {
                    end++;
                }
                String referenced = content.substring(index, end);
                if (!subprojectClasses.contains(referenced)) {
                    fail(subproject + " class " + classFile.getFileName()
                        + " references the root project class " + referenced.replace('/', '.'));
                }
                index = content.indexOf(ROOT_PACKAGE_BINARY, end);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + classFile, e);
        }
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '/' || c == '$' || c == '_';
    }
}
