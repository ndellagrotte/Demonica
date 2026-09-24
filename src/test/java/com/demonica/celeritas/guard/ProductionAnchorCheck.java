package com.demonica.celeritas.guard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * {@code verifyProductionAnchors}: the distributed jar's anchors, checked as QuarantineGuard checks them in a real
 * install (through the jar's refmap, against the SRG-named Celeritas jar from Maven), must all hold on the pin. The unit
 * tests check the same anchors against the development remap, where no refmap is involved.
 *
 * <p>Arguments: the pinned Celeritas jar, the remapped Demonica jar.
 */
public final class ProductionAnchorCheck {
    private ProductionAnchorCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path celeritas = Path.of(args[0]);
        Path mod = Path.of(args[1]);
        AnchorFile.Contents contents;
        Map<String, Map<String, String>> refmap = new HashMap<>();
        try (JarFile jar = new JarFile(mod.toFile())) {
            try (Reader reader = reader(jar, AnchorFile.RESOURCE)) {
                contents = AnchorFile.read(reader);
            }
            try (Reader reader = reader(jar, "mixins.demonica-refmap.json")) {
                JsonObject mappings = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("mappings");
                for (Map.Entry<String, JsonElement> mixin : mappings.entrySet()) {
                    Map<String, String> references = new HashMap<>();
                    mixin.getValue().getAsJsonObject().entrySet().forEach(e -> references.put(e.getKey(), e.getValue().getAsString()));
                    refmap.put(mixin.getKey(), references);
                }
            }
        }
        List<AnchorAudit.Failure> failures = new AnchorAudit(ClassIndex.ofJar(celeritas), refmap).audit(contents.anchors());
        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder(failures.size() + " of " + contents.anchors().size() + " anchors in "
                + mod.getFileName() + " do not hold against " + celeritas.getFileName() + " in production names:");
            failures.forEach(failure -> message.append("\n  ").append(failure));
            throw new IllegalStateException(message.toString());
        }
        System.out.println("All " + contents.anchors().size() + " anchors in " + mod.getFileName() + " hold against "
            + celeritas.getFileName() + " through the refmap");
    }

    private static Reader reader(JarFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null) {
            throw new IOException(jar.getName() + " has no " + name);
        }
        return new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8);
    }
}
