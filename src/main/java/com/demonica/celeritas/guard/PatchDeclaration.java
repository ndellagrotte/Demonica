package com.demonica.celeritas.guard;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

/** A quarantine mixin's {@link Patch}, read from its class file without loading the class. */
record PatchDeclaration(List<String> ids, PatchGroup group) {
    private static final String PATCH = "Lcom/demonica/celeritas/guard/Patch;";

    /** The {@link Patch} of {@code mixin} (read with or without code), or null if it has none. */
    @SuppressWarnings("unchecked")
    static @Nullable PatchDeclaration read(ClassNode mixin) {
        for (AnnotationNode annotation : mixin.invisibleAnnotations != null ? mixin.invisibleAnnotations : List.<AnnotationNode>of()) {
            if (!PATCH.equals(annotation.desc) || annotation.values == null) {
                continue;
            }
            List<String> ids = null;
            PatchGroup group = null;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                Object value = annotation.values.get(i + 1);
                switch ((String) annotation.values.get(i)) {
                    case "value" -> ids = List.copyOf((List<String>) value);
                    case "group" -> group = PatchGroup.valueOf(((String[]) value)[1]);
                    default -> { }
                }
            }
            return ids != null && group != null ? new PatchDeclaration(ids, group) : null;
        }
        return null;
    }
}
