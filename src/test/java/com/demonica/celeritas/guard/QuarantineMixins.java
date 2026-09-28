package com.demonica.celeritas.guard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The quarantine's mixins and their {@link Patch} declarations, read from the compiled classes without loading them. */
public final class QuarantineMixins {
    public static final String CONFIG = "mixins.demonica.celeritas.json";

    private QuarantineMixins() {
    }

    /** The quarantine config's mixins, as binary names. */
    public static List<String> names() {
        try (InputStream in = resource(CONFIG); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return configMixins(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The mixins a mixin config lists, as binary names, in the order it lists them. */
    public static List<String> configMixins(Reader config) {
        JsonObject root = JsonParser.parseReader(config).getAsJsonObject();
        String mixinPackage = root.get("package").getAsString();
        List<String> mixins = new ArrayList<>();
        for (String section : List.of("mixins", "client", "server")) {
            JsonArray entries = root.getAsJsonArray(section);
            if (entries != null) {
                for (JsonElement entry : entries) {
                    mixins.add(mixinPackage + "." + entry.getAsString());
                }
            }
        }
        return mixins;
    }

    /** Each quarantine mixin's {@link Patch}, by binary name; throws for a mixin without one. */
    static Map<String, PatchDeclaration> declarations() {
        Map<String, PatchDeclaration> declarations = new LinkedHashMap<>();
        for (String name : names()) {
            ClassNode node = new ClassNode();
            try (InputStream in = resource(name.replace('.', '/') + ".class")) {
                new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            PatchDeclaration declaration = PatchDeclaration.read(node);
            if (declaration == null) {
                throw new IllegalStateException(name + " has no @Patch");
            }
            declarations.put(name, declaration);
        }
        return declarations;
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = QuarantineMixins.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException(name + " is not on the test classpath");
        }
        return in;
    }
}
