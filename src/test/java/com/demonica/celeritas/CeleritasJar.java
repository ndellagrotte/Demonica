package com.demonica.celeritas;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The pinned Celeritas mod jar as the tests see it: the dev (MCP-named) remap that Unimined puts on the
 * compile classpath. Tests read class bytes only; nothing here loads a Celeritas class.
 */
public final class CeleritasJar {
    private static final String JAR_PREFIX = "celeritas-forge-mc12.2-";

    private static volatile CeleritasJar instance;

    private final File file;
    private final Map<String, byte[]> classes;

    private CeleritasJar(File file) throws IOException {
        this.file = file;
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(file)) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    String internalName = entry.getName().substring(0, entry.getName().length() - ".class".length());
                    classes.put(internalName, in.readAllBytes());
                }
            }
        }
        this.classes = Collections.unmodifiableMap(classes);
    }

    public static CeleritasJar get() {
        CeleritasJar jar = instance;
        if (jar == null) {
            synchronized (CeleritasJar.class) {
                jar = instance;
                if (jar == null) {
                    try {
                        jar = instance = new CeleritasJar(locate());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        return jar;
    }

    private static File locate() {
        List<File> candidates = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File file = new File(entry);
            String name = file.getName();
            if (name.startsWith(JAR_PREFIX) && name.endsWith(".jar") && !name.endsWith("-sources.jar")) {
                candidates.add(file);
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalStateException("Expected exactly one " + JAR_PREFIX + "*.jar on the test classpath, found "
                + candidates + ". The pinned Celeritas mod is declared as modCompileOnly in build.gradle.");
        }
        return candidates.get(0);
    }

    public File file() {
        return this.file;
    }

    /** Internal names of every class in the jar, in jar order. */
    public Iterable<String> classNames() {
        return this.classes.keySet();
    }

    public boolean contains(String internalName) {
        return this.classes.containsKey(internalName);
    }

    public byte[] bytes(String internalName) {
        byte[] bytes = this.classes.get(internalName);
        if (bytes == null) {
            throw new IllegalArgumentException(internalName + " is not in " + this.file.getName());
        }
        return bytes.clone();
    }

    public ClassNode node(String internalName) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes(internalName)).accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }
}
