package com.demonica.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsMixinConfigurationTest {
    private static final String CONFIG = "mixins.demonica.diagnostics.json";
    private static final String MIXIN_DESCRIPTOR = Type.getDescriptor(Mixin.class);

    @Test
    void everyCompiledMixinIsDeclaredExactlyOnce() throws IOException, URISyntaxException {
        JsonObject config = readConfig();
        String prefix = config.get("package").getAsString() + ".";
        List<String> declared = Stream.of("mixins", "client", "server")
            .flatMap(key -> stream(config.getAsJsonArray(key)))
            .map(name -> prefix + name)
            .toList();

        assertEquals(declared.size(), new HashSet<>(declared).size(), "a mixin is declared twice: " + declared);
        assertEquals(compiledMixins(), Set.copyOf(declared));
    }

    @Test
    void configUsesItsOwnRefmapAndNeverStopsTheGame() throws IOException {
        JsonObject config = readConfig();

        // Demonica's refmap is mixins.demonica-refmap.json; both jars share the LaunchClassLoader.
        assertEquals("mixins.demonica.diagnostics-refmap.json", config.get("refmap").getAsString());
        assertFalse(config.get("required").getAsBoolean(), "a diagnostics mixin that misses must not stop the game");
        assertEquals("0.8.5", config.get("minVersion").getAsString());
        assertEquals("JAVA_8", config.get("compatibilityLevel").getAsString());
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
    }

    @Test
    void theCoremodRegistersTheConfig() throws IOException {
        ClassNode coremod = readClass(DiagnosticsCoremod.class.getName().replace('.', '/') + ".class");
        boolean found = coremod.methods.stream()
            .flatMap(method -> Stream.of(method.instructions.toArray()))
            .anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                && CONFIG.equals(ldc.cst));

        assertTrue(found, "DiagnosticsCoremod must name " + CONFIG);
    }

    private static Set<String> compiledMixins() throws IOException, URISyntaxException {
        Path classes = Path.of(DiagnosticsCoremod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> mixins = new HashSet<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassNode node;
                try (InputStream stream = Files.newInputStream(file)) {
                    node = new ClassNode();
                    new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
                }
                if (hasMixinAnnotation(node)) {
                    mixins.add(node.name.replace('/', '.'));
                }
            }
        }
        return mixins;
    }

    private static boolean hasMixinAnnotation(ClassNode node) {
        return Stream.of(node.invisibleAnnotations, node.visibleAnnotations)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map((AnnotationNode annotation) -> annotation.desc)
            .anyMatch(MIXIN_DESCRIPTOR::equals);
    }

    private static Stream<String> stream(JsonArray array) {
        return array == null ? Stream.empty() : array.asList().stream().map(JsonElement::getAsString);
    }

    private static JsonObject readConfig() throws IOException {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
            DiagnosticsMixinConfigurationTest.class.getClassLoader().getResourceAsStream(CONFIG), CONFIG),
            StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static ClassNode readClass(String resource) throws IOException {
        try (InputStream stream = Objects.requireNonNull(
            DiagnosticsMixinConfigurationTest.class.getClassLoader().getResourceAsStream(resource), resource)) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
