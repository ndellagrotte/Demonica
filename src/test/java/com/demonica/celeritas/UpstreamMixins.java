package com.demonica.celeritas;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/** Upstream Celeritas's own mixins, read from the pinned jar's class bytes: their targets, priorities and overwrites. */
final class UpstreamMixins {
    static final String MIXIN_PACKAGE = "org/taumc/celeritas/mixin/";
    static final int DEFAULT_PRIORITY = 1000;
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    /** One upstream mixin class; {@code overwrites} are {@code name + desc} of its {@code @Overwrite} methods. */
    record MixinClass(String name, List<String> targets, int priority, List<String> overwrites) {
    }

    private UpstreamMixins() {
    }

    static List<MixinClass> read(CeleritasJar jar) {
        List<MixinClass> mixins = new ArrayList<>();
        for (String className : jar.classNames()) {
            if (!className.startsWith(MIXIN_PACKAGE)) {
                continue;
            }
            ClassNode node = jar.node(className);
            AnnotationNode mixin = annotation(node.invisibleAnnotations, MIXIN);
            if (mixin == null) {
                mixin = annotation(node.visibleAnnotations, MIXIN);
            }
            if (mixin == null) {
                continue;
            }
            List<String> targets = new ArrayList<>();
            int priority = DEFAULT_PRIORITY;
            for (int i = 0; i + 1 < mixin.values.size(); i += 2) {
                Object value = mixin.values.get(i + 1);
                switch ((String) mixin.values.get(i)) {
                    case "value" -> ((List<?>) value).forEach(t -> targets.add(((Type) t).getInternalName()));
                    case "targets" -> ((List<?>) value).forEach(t -> targets.add(((String) t).replace('.', '/')));
                    case "priority" -> priority = (Integer) value;
                    default -> { }
                }
            }
            List<String> overwrites = new ArrayList<>();
            for (MethodNode method : node.methods) {
                if (annotation(method.invisibleAnnotations, OVERWRITE) != null || annotation(method.visibleAnnotations, OVERWRITE) != null) {
                    overwrites.add(method.name + method.desc);
                }
            }
            mixins.add(new MixinClass(className, List.copyOf(targets), priority, List.copyOf(overwrites)));
        }
        return mixins;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.equals(desc)).findFirst().orElse(null);
    }
}
