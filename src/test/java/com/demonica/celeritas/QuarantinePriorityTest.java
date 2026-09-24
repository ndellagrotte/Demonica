package com.demonica.celeritas;

import com.demonica.celeritas.guard.AnchorExtractor;
import com.demonica.celeritas.guard.QuarantineAnchors;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quarantine's rules against upstream's own mixins (docs/celeritas/LEDGER.md, "Rules"): an injection into a method
 * that another mixin merged needs a strictly higher priority, a second {@code @Overwrite} of the same method is skipped
 * without a word, and a second {@code @Redirect} of the same call fails.
 */
class QuarantinePriorityTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    @Test
    void everyQuarantineMixinOutranksUpstreamOnItsTargets() throws IOException {
        Map<String, Integer> upstream = new HashMap<>();
        for (UpstreamMixinInventory.MixinClass mixin : UpstreamMixinInventory.read(CeleritasJar.get())) {
            mixin.targets().forEach(target -> upstream.merge(target, mixin.priority(), Math::max));
        }
        List<String> outranked = new ArrayList<>();
        for (String name : QuarantineAnchors.mixins()) {
            AnnotationNode mixin = annotation(read(name).invisibleAnnotations, MIXIN);
            int priority = value(mixin, "priority") instanceof Integer declared ? declared : UpstreamMixinInventory.DEFAULT_PRIORITY;
            for (String target : targets(mixin)) {
                // Where upstream has no mixin, another mod's mixin at the default priority may still have merged the method.
                int upstreamPriority = upstream.getOrDefault(target, UpstreamMixinInventory.DEFAULT_PRIORITY);
                if (priority <= upstreamPriority) {
                    outranked.add(name + " -> " + target + ": priority " + priority + ", upstream " + upstreamPriority);
                }
            }
        }
        assertTrue(outranked.isEmpty(), "quarantine mixins not above upstream's priority:\n  " + String.join("\n  ", outranked));
    }

    @Test
    void theQuarantineNeitherOverwritesNorRedirects() {
        List<String> found = new ArrayList<>();
        for (String name : QuarantineAnchors.mixins()) {
            for (MethodNode method : read(name).methods) {
                for (String forbidden : List.of(OVERWRITE, REDIRECT)) {
                    if (annotation(method.invisibleAnnotations, forbidden) != null || annotation(method.visibleAnnotations, forbidden) != null) {
                        found.add(name + "." + method.name + ": " + Type.getType(forbidden).getClassName());
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "the quarantine patches with @Inject, @ModifyArg, @WrapOperation and the like:\n  "
            + String.join("\n  ", found));
    }

    /** Across every Demonica mixin config, not only the quarantine: a duplicate {@code @Overwrite} is silently skipped. */
    @Test
    void noDemonicaMixinOverwritesWhatUpstreamOverwrites() throws IOException {
        List<String> duplicates = new ArrayList<>();
        for (String name : allDemonicaMixins()) {
            ClassNode mixin = read(name);
            AnnotationNode annotation = annotation(mixin.invisibleAnnotations, MIXIN);
            if (annotation == null) {
                continue;
            }
            for (MethodNode method : mixin.methods) {
                if (annotation(method.invisibleAnnotations, OVERWRITE) == null && annotation(method.visibleAnnotations, OVERWRITE) == null) {
                    continue;
                }
                for (String target : targets(annotation)) {
                    if (AnchorInventoryTest.UPSTREAM_OVERWRITES.contains(target + "#" + method.name + method.desc)) {
                        duplicates.add(name + " overwrites " + target + "." + method.name + method.desc);
                    }
                }
            }
        }
        assertTrue(duplicates.isEmpty(), "members upstream already overwrites:\n  " + String.join("\n  ", duplicates));
    }

    private static List<String> allDemonicaMixins() throws IOException {
        Path resources = Path.of(System.getProperty("demonica.projectRoot", "."), "src/main/resources");
        List<String> mixins = new ArrayList<>();
        try (DirectoryStream<Path> configs = Files.newDirectoryStream(resources, "mixins.demonica.*.json")) {
            for (Path config : configs) {
                try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                    mixins.addAll(AnchorExtractor.configMixins(reader));
                }
            }
        }
        assertTrue(mixins.size() > 50, "found only " + mixins.size() + " mixins in " + resources);
        return mixins;
    }

    private static ClassNode read(String binaryName) {
        try (InputStream in = QuarantinePriorityTest.class.getClassLoader().getResourceAsStream(binaryName.replace('.', '/') + ".class")) {
            if (in == null) {
                throw new IllegalStateException(binaryName + " is not compiled");
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> targets(AnnotationNode mixin) {
        List<String> targets = new ArrayList<>();
        if (value(mixin, "value") instanceof List<?> types) {
            types.forEach(type -> targets.add(((Type) type).getInternalName()));
        }
        if (value(mixin, "targets") instanceof List<?> names) {
            names.forEach(name -> targets.add(((String) name).replace('.', '/')));
        }
        return targets;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.equals(desc)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }
}
