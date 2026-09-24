package com.demonica.celeritas.guard;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Class bytes that can be listed by package: class directories or a jar. The build uses it to read the compiled
 * quarantine mixins and the pinned Celeritas jar ({@link AnchorExtractor}); nothing here loads a class.
 */
public interface ClassIndex extends AnchorAudit.ClassSource {
    /** Internal names of the classes whose names start with {@code prefix} (an internal name or a package ending in /). */
    List<String> classesStartingWith(String prefix) throws IOException;

    /** The classes under the given roots; a class found in two roots is read from the first. */
    static ClassIndex ofDirectories(List<Path> roots) {
        return new ClassIndex() {
            @Override
            public byte @Nullable [] bytes(String internalName) throws IOException {
                for (Path root : roots) {
                    Path file = root.resolve(internalName + ".class");
                    if (Files.isRegularFile(file)) {
                        return Files.readAllBytes(file);
                    }
                }
                return null;
            }

            @Override
            public List<String> classesStartingWith(String prefix) throws IOException {
                List<String> names = new ArrayList<>();
                for (Path root : roots) {
                    if (!Files.isDirectory(root)) {
                        continue;
                    }
                    try (Stream<Path> files = Files.walk(root)) {
                        files.filter(file -> file.toString().endsWith(".class")).forEach(file -> {
                            String relative = root.relativize(file).toString().replace('\\', '/');
                            String name = relative.substring(0, relative.length() - ".class".length());
                            if (name.startsWith(prefix) && !names.contains(name)) {
                                names.add(name);
                            }
                        });
                    }
                }
                Collections.sort(names);
                return names;
            }
        };
    }

    /** Every class of a jar, read into memory once. */
    static ClassIndex ofJar(Path jar) throws IOException {
        Map<String, byte[]> classes = new TreeMap<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(file.entries())) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = file.getInputStream(entry)) {
                    classes.put(entry.getName().substring(0, entry.getName().length() - ".class".length()), in.readAllBytes());
                }
            }
        }
        return new ClassIndex() {
            @Override
            public byte @Nullable [] bytes(String internalName) {
                byte[] bytes = classes.get(internalName);
                return bytes != null ? bytes.clone() : null;
            }

            @Override
            public List<String> classesStartingWith(String prefix) {
                return classes.keySet().stream().filter(name -> name.startsWith(prefix)).toList();
            }
        };
    }
}
