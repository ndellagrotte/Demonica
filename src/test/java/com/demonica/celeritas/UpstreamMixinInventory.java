package com.demonica.celeritas;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Reads upstream Celeritas's own mixins out of the pinned jar: their targets, priorities, overwrites and
 * injection points. Injector method strings are resolved through the jar's refmap, so every entry is in MCP
 * names whichever way the jar was remapped.
 */
public final class UpstreamMixinInventory {
    public static final String MIXIN_PACKAGE = "org/taumc/celeritas/mixin/";
    public static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    public static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    public static final int DEFAULT_PRIORITY = 1000;

    private static final Map<String, String> INJECTORS = Map.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;", "inject",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;", "redirect",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "modifyarg",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", "modifyargs",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", "modifyvariable",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", "modifyconstant",
        "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", "wrapoperation",
        "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", "modifyexpressionvalue",
        "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;", "modifyreturnvalue",
        "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;", "wrapmethod"
    );

    /** One upstream mixin class. */
    public record MixinClass(String name, List<String> targets, int priority, List<String> overwrites, List<String> injections) {}

    private UpstreamMixinInventory() {
    }

    public static List<MixinClass> read(CeleritasJar jar) throws IOException {
        Map<String, Map<String, String>> refmap = readRefmap(jar);
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
                String key = (String) mixin.values.get(i);
                Object value = mixin.values.get(i + 1);
                switch (key) {
                    case "value" -> ((List<?>) value).forEach(t -> targets.add(((Type) t).getInternalName()));
                    case "targets" -> ((List<?>) value).forEach(t -> targets.add(((String) t).replace('.', '/')));
                    case "priority" -> priority = (Integer) value;
                    default -> { }
                }
            }
            Collections.sort(targets);

            Map<String, String> classRefs = refmap.getOrDefault(className, Map.of());
            List<String> overwrites = new ArrayList<>();
            List<String> injections = new ArrayList<>();
            for (MethodNode method : node.methods) {
                if (annotation(method.invisibleAnnotations, OVERWRITE) != null || annotation(method.visibleAnnotations, OVERWRITE) != null) {
                    overwrites.add(method.name + method.desc);
                }
                for (AnnotationNode annotation : annotations(method)) {
                    String kind = INJECTORS.get(annotation.desc);
                    if (kind == null) {
                        continue;
                    }
                    for (String target : stringList(annotation, "method")) {
                        injections.add(kind + " " + stripOwner(classRefs.getOrDefault(target, target)) + at(annotation, classRefs));
                    }
                }
            }
            Collections.sort(overwrites);
            Collections.sort(injections);
            mixins.add(new MixinClass(className, targets, priority, overwrites, injections));
        }
        mixins.sort((a, b) -> a.name().compareTo(b.name()));
        return mixins;
    }

    /** A stable, line-per-fact rendering used as the checked-in snapshot. */
    public static List<String> render(List<MixinClass> mixins) {
        List<String> lines = new ArrayList<>();
        for (MixinClass mixin : mixins) {
            String name = mixin.name().substring(MIXIN_PACKAGE.length());
            lines.add(name + " -> " + String.join(",", mixin.targets()) + " priority=" + mixin.priority());
            mixin.overwrites().forEach(o -> lines.add(name + "   overwrite " + o));
            mixin.injections().forEach(i -> lines.add(name + "   " + i));
        }
        return lines;
    }

    private static String at(AnnotationNode injector, Map<String, String> refs) {
        Object at = value(injector, "at");
        List<?> ats = at instanceof List<?> list ? list : at == null ? List.of() : List.of(at);
        List<String> points = new ArrayList<>();
        for (Object element : ats) {
            if (!(element instanceof AnnotationNode atNode)) {
                continue;
            }
            Object point = value(atNode, "value");
            Object target = value(atNode, "target");
            String rendered = String.valueOf(point);
            if (target instanceof String s && !s.isEmpty()) {
                rendered += ":" + refs.getOrDefault(s, s);
            }
            Object ordinal = value(atNode, "ordinal");
            if (ordinal != null) {
                rendered += "#" + ordinal;
            }
            points.add(rendered);
        }
        return points.isEmpty() ? "" : " @ " + String.join(" | ", points);
    }

    /** Refmap targets carry the owner ({@code Lowner;name(desc)}); the mixin target already names it. */
    private static String stripOwner(String target) {
        if (target.startsWith("L")) {
            int end = target.indexOf(';');
            if (end > 0) {
                return target.substring(end + 1);
            }
        }
        return target;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> all = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            all.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            all.addAll(method.invisibleAnnotations);
        }
        return all;
    }

    static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(desc)) {
                return annotation;
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static List<String> stringList(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            list.forEach(v -> strings.add((String) v));
            return strings;
        }
        return value instanceof String s ? List.of(s) : List.of();
    }

    private static Map<String, Map<String, String>> readRefmap(CeleritasJar jar) throws IOException {
        Map<String, Map<String, String>> refmap = new HashMap<>();
        try (JarFile file = new JarFile(jar.file())) {
            ZipEntry entry = file.getEntry("mixins.celeritas-refmap.json");
            if (entry == null) {
                return refmap;
            }
            try (InputStream in = file.getInputStream(entry)) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject mappings = root.getAsJsonObject("mappings");
                if (mappings == null) {
                    return refmap;
                }
                for (Map.Entry<String, JsonElement> mixin : mappings.entrySet()) {
                    Map<String, String> refs = new HashMap<>();
                    mixin.getValue().getAsJsonObject().entrySet().forEach(e -> refs.put(e.getKey(), e.getValue().getAsString()));
                    refmap.put(mixin.getKey(), refs);
                }
            }
        }
        return refmap;
    }
}
