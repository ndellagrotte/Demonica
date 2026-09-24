package com.demonica.celeritas.guard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives the anchors of the quarantine's patches (docs/celeritas/LEDGER.md) from the compiled mixins of
 * {@code mixins.demonica.celeritas.json}. For each mixin, read with its {@link Patch} declaration:
 * <ul>
 *   <li>its Celeritas targets, and the fields and methods it shadows in them;</li>
 *   <li>the methods its injectors select, and the calls and field accesses their {@code @At}s name. For a vanilla
 *   target, only methods that one of upstream's mixins overwrites are Celeritas's, and their bodies are checked in
 *   that mixin;</li>
 *   <li>the methods it adds that override one of a target's supertypes;</li>
 *   <li>every Celeritas class and member its own code uses, and the code of the classes {@link Patch#uses} names;</li>
 *   <li>its {@link Patch#context} declarations.</li>
 * </ul>
 * The build writes the result into the mod jar ({@link AnchorFile}); {@code AnchorInventoryTest} checks it against the
 * pinned jar. It reads class bytes only.
 */
public final class AnchorExtractor {
    public static final String QUARANTINE_CONFIG = "mixins.demonica.celeritas.json";

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String PATCH = "Lcom/demonica/celeritas/guard/Patch;";
    private static final String UPSTREAM_MIXINS = "org/taumc/celeritas/mixin/";
    private static final Set<String> INJECTORS = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
        "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
        "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
        "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;",
        "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
    private static final Set<String> INVOKE_POINTS = Set.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING");
    private static final Comparator<Anchor> ORDER = Comparator.comparing(Anchor::kind)
        .thenComparing(Anchor::owner).thenComparing(Anchor::member).thenComparing(Anchor::target);

    private final ClassIndex demonica;
    private final ClassIndex celeritas;

    private AnchorExtractor(ClassIndex demonica, ClassIndex celeritas) {
        this.demonica = demonica;
        this.celeritas = celeritas;
    }

    /**
     * The anchors of the given quarantine mixins, grouped by mixin in the order given.
     *
     * @param demonica  Demonica's compiled classes (the mixins and the classes they use)
     * @param celeritas the pinned Celeritas jar as the build compiles against it, for upstream's mixins and supertypes
     * @throws IllegalStateException if a mixin breaks a quarantine rule the anchors depend on: no {@link Patch}, a
     *                               {@code uses} entry that names nothing, or a method replaced without {@code @Overwrite}
     */
    public static List<Anchor> extract(List<String> mixins, ClassIndex demonica, ClassIndex celeritas) throws IOException {
        AnchorExtractor extractor = new AnchorExtractor(demonica, celeritas);
        List<Anchor> anchors = new ArrayList<>();
        for (String mixin : mixins) {
            anchors.addAll(extractor.extract(mixin));
        }
        return anchors;
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

    private List<Anchor> extract(String mixinName) throws IOException {
        ClassNode mixin = read(this.demonica, mixinName.replace('.', '/'), true);
        if (mixin == null) {
            throw new IllegalStateException("quarantine mixin " + mixinName + " is not compiled");
        }
        AnnotationNode mixinAnnotation = annotation(mixin.invisibleAnnotations, mixin.visibleAnnotations, MIXIN);
        AnnotationNode patch = annotation(mixin.invisibleAnnotations, mixin.visibleAnnotations, PATCH);
        if (mixinAnnotation == null || patch == null) {
            throw new IllegalStateException(mixinName + " must be a @Mixin and declare its ledger patch with @Patch");
        }
        PatchGroup group = PatchGroup.valueOf(enumName(value(patch, "group")));
        String patches = String.join(",", strings(patch, "value"));
        Set<Anchor> anchors = new LinkedHashSet<>();
        Sink sink = (kind, owner, member, target) -> anchors.add(new Anchor(mixinName, group, patches, kind, owner, member, target));

        for (String target : targets(mixinAnnotation)) {
            boolean celeritasTarget = AnchorAudit.isCeleritas(target);
            if (celeritasTarget) {
                sink.add(Anchor.Kind.CLASS, target, "", "");
                for (FieldNode field : mixin.fields) {
                    if (annotation(field.invisibleAnnotations, field.visibleAnnotations, SHADOW) != null) {
                        sink.add(Anchor.Kind.FIELD, target, field.name + ":" + field.desc, "");
                    }
                }
            }
            for (MethodNode method : mixin.methods) {
                AnnotationNode injector = injector(method);
                if (annotation(method.invisibleAnnotations, method.visibleAnnotations, SHADOW) != null) {
                    if (celeritasTarget) {
                        sink.add(Anchor.Kind.METHOD, target, method.name + method.desc, "");
                    }
                } else if (injector != null) {
                    injectorAnchors(sink, target, celeritasTarget, injector);
                } else if (celeritasTarget && isAdded(method)) {
                    addedMethodAnchor(sink, mixinName, target, method);
                }
            }
        }
        codeAnchors(sink, mixin, true);
        for (String context : strings(patch, "context")) {
            contextAnchor(sink, mixinName, context);
        }
        for (String used : usedClasses(mixinName, patch)) {
            ClassNode node = read(this.demonica, used, true);
            if (node != null) {
                codeAnchors(sink, node, false);
            }
        }
        List<Anchor> sorted = new ArrayList<>(anchors);
        sorted.sort(ORDER);
        return sorted;
    }

    @FunctionalInterface
    private interface Sink {
        void add(Anchor.Kind kind, String owner, String member, String target);
    }

    private void injectorAnchors(Sink sink, String target, boolean celeritasTarget, AnnotationNode injector) throws IOException {
        for (String selector : strings(injector, "method")) {
            // A vanilla method's body is Celeritas's only where upstream overwrites it; then it is checked in that mixin.
            String bodyOwner = celeritasTarget ? target : upstreamOverwriter(target, MemberRef.parse(selector));
            if (bodyOwner == null) {
                continue;
            }
            sink.add(Anchor.Kind.METHOD, bodyOwner, selector, "");
            for (AnnotationNode at : annotations(value(injector, "at"))) {
                Object point = value(at, "value");
                Object atTarget = value(at, "target");
                if (!(atTarget instanceof String reference) || reference.isEmpty()) {
                    continue;
                }
                if (INVOKE_POINTS.contains(point)) {
                    sink.add(Anchor.Kind.INVOKE, bodyOwner, selector, reference);
                } else if ("FIELD".equals(point)) {
                    sink.add(Anchor.Kind.ACCESS, bodyOwner, selector, reference);
                }
            }
        }
    }

    /** Upstream's mixin that {@code @Overwrite}s the selected method of a vanilla class, or null if none does. */
    private @Nullable String upstreamOverwriter(String target, MemberRef selector) throws IOException {
        for (String name : this.celeritas.classesStartingWith(UPSTREAM_MIXINS)) {
            ClassNode upstream = read(this.celeritas, name, false);
            AnnotationNode mixin = upstream == null ? null : annotation(upstream.invisibleAnnotations, upstream.visibleAnnotations, MIXIN);
            if (mixin == null || !targets(mixin).contains(target)) {
                continue;
            }
            for (MethodNode method : upstream.methods) {
                if (selector.selects(method.name, method.desc)
                    && annotation(method.invisibleAnnotations, method.visibleAnnotations, OVERWRITE) != null) {
                    return name;
                }
            }
        }
        return null;
    }

    /** A method the mixin adds that overrides a target supertype's: the override is the patch, so the supertype's must stay. */
    private void addedMethodAnchor(Sink sink, String mixinName, String target, MethodNode method) throws IOException {
        ClassNode targetNode = read(this.celeritas, target, false);
        if (targetNode == null) {
            return;
        }
        for (MethodNode existing : targetNode.methods) {
            if (existing.name.equals(method.name) && existing.desc.equals(method.desc)) {
                throw new IllegalStateException(mixinName + "." + method.name + method.desc + " replaces " + target
                    + "'s method without @Overwrite, which the quarantine does not allow");
            }
        }
        if (declaredBySupertype(targetNode, method.name, method.desc)) {
            sink.add(Anchor.Kind.INHERITED, target, method.name + method.desc, "");
        }
    }

    private boolean declaredBySupertype(ClassNode type, String name, String desc) throws IOException {
        List<String> supertypes = new ArrayList<>(type.interfaces);
        if (type.superName != null) {
            supertypes.add(type.superName);
        }
        for (String supertype : supertypes) {
            ClassNode node = AnchorAudit.isCeleritas(supertype) ? read(this.celeritas, supertype, false) : null;
            if (node == null) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    return true;
                }
            }
            if (declaredBySupertype(node, name, desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdded(MethodNode method) {
        if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0
            || method.name.startsWith("<")) {
            return false;
        }
        for (List<AnnotationNode> annotations : Arrays.asList(method.invisibleAnnotations, method.visibleAnnotations)) {
            if (annotations == null) {
                continue;
            }
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/") || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/")) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Every Celeritas class and member the code of {@code node} uses. A mixin's constructors call its superclass's,
     * but Mixin merges only their field initializers, so that call is not an anchor.
     */
    private static void codeAnchors(Sink sink, ClassNode node, boolean mixin) {
        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            boolean constructor = method.name.equals("<init>");
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (mixin && constructor && insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.name.equals("<init>") && call.owner.equals(node.superName)) {
                    continue;
                }
                if (insn instanceof MethodInsnNode call && AnchorAudit.isCeleritas(call.owner)) {
                    sink.add(Anchor.Kind.MEMBER, call.owner, call.name + call.desc, "");
                } else if (insn instanceof FieldInsnNode field && AnchorAudit.isCeleritas(field.owner)) {
                    sink.add(Anchor.Kind.MEMBER, field.owner, field.name + ":" + field.desc, "");
                } else if (insn instanceof TypeInsnNode type) {
                    classAnchor(sink, type.desc.startsWith("[") ? Type.getType(type.desc) : Type.getObjectType(type.desc));
                } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
                    classAnchor(sink, type);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object argument : indy.bsmArgs) {
                        if (argument instanceof Handle handle && AnchorAudit.isCeleritas(handle.getOwner())) {
                            boolean field = handle.getTag() <= Opcodes.H_PUTSTATIC;
                            sink.add(Anchor.Kind.MEMBER, handle.getOwner(), handle.getName() + (field ? ":" : "") + handle.getDesc(), "");
                        }
                    }
                }
            }
        }
    }

    private static void classAnchor(Sink sink, Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (element.getSort() == Type.OBJECT && AnchorAudit.isCeleritas(element.getInternalName())) {
            sink.add(Anchor.Kind.CLASS, element.getInternalName(), "", "");
        }
    }

    private static void contextAnchor(Sink sink, String mixinName, String context) {
        if (context.startsWith("absent ")) {
            MemberRef method = ownedReference(mixinName, context, context.substring("absent ".length()));
            sink.add(Anchor.Kind.ABSENT, method.owner(), member(method), "");
            return;
        }
        int calls = context.indexOf(" calls ");
        if (calls >= 0) {
            MemberRef caller = ownedReference(mixinName, context, context.substring(0, calls));
            String callee = context.substring(calls + " calls ".length());
            ownedReference(mixinName, context, callee);
            sink.add(Anchor.Kind.INVOKE, caller.owner(), member(caller), callee);
            return;
        }
        MemberRef reference = ownedReference(mixinName, context, context);
        sink.add(Anchor.Kind.MEMBER, reference.owner(), member(reference), "");
    }

    /**
     * A member a context names. It must be Celeritas's own: a vanilla member has another name in production, and the
     * refmap covers only what injector annotations write.
     */
    private static MemberRef ownedReference(String mixinName, String context, String reference) {
        MemberRef parsed = MemberRef.parse(reference.trim());
        if (parsed.owner() == null || parsed.desc() == null || !AnchorAudit.isCeleritas(parsed.owner())) {
            throw new IllegalStateException(mixinName + ": @Patch context \"" + context
                + "\" must name a Celeritas member as Lowner;name(desc) or Lowner;name:desc");
        }
        return parsed;
    }

    private static String member(MemberRef reference) {
        return reference.name() + (reference.field() ? ":" : "") + reference.desc();
    }

    /** The classes named by {@link Patch#uses} and {@link Patch#usesPackages}, with their nested classes. */
    private List<String> usedClasses(String mixinName, AnnotationNode patch) throws IOException {
        List<String> classes = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        Object uses = value(patch, "uses");
        if (uses instanceof List<?> types) {
            for (Object type : types) {
                String name = ((Type) type).getInternalName();
                if (this.demonica.bytes(name) == null) {
                    throw new IllegalStateException(mixinName + ": @Patch uses " + name + ", which is not compiled");
                }
                classes.add(name);
                prefixes.add(name + "$");
            }
        }
        for (String packagePrefix : strings(patch, "usesPackages")) {
            if (!packagePrefix.endsWith("/")) {
                throw new IllegalStateException(mixinName + ": @Patch usesPackages entry " + packagePrefix + " must end in /");
            }
            prefixes.add(packagePrefix);
        }
        for (String prefix : prefixes) {
            List<String> matched = this.demonica.classesStartingWith(prefix);
            if (matched.isEmpty() && !prefix.endsWith("$")) {
                throw new IllegalStateException(mixinName + ": @Patch usesPackages " + prefix + " contains no class");
            }
            for (String name : matched) {
                if (!classes.contains(name)) {
                    classes.add(name);
                }
            }
        }
        return classes;
    }

    private static @Nullable ClassNode read(AnchorAudit.ClassSource source, String internalName, boolean code) throws IOException {
        byte[] bytes = source.bytes(internalName);
        if (bytes == null) {
            return null;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, code ? ClassReader.SKIP_FRAMES : ClassReader.SKIP_CODE);
        return node;
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

    private static @Nullable AnnotationNode injector(MethodNode method) {
        for (List<AnnotationNode> annotations : Arrays.asList(method.invisibleAnnotations, method.visibleAnnotations)) {
            if (annotations == null) {
                continue;
            }
            for (AnnotationNode annotation : annotations) {
                if (INJECTORS.contains(annotation.desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static @Nullable AnnotationNode annotation(@Nullable List<AnnotationNode> invisible, @Nullable List<AnnotationNode> visible,
                                                       String desc) {
        for (List<AnnotationNode> annotations : Arrays.asList(invisible, visible)) {
            if (annotations == null) {
                continue;
            }
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static @Nullable Object value(AnnotationNode annotation, String key) {
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

    private static List<String> strings(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            list.forEach(element -> strings.add((String) element));
            return strings;
        }
        return value instanceof String string ? List.of(string) : List.of();
    }

    private static List<AnnotationNode> annotations(@Nullable Object value) {
        if (value instanceof AnnotationNode single) {
            return List.of(single);
        }
        List<AnnotationNode> annotations = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(element -> annotations.add((AnnotationNode) element));
        }
        return annotations;
    }

    private static String enumName(@Nullable Object value) {
        if (value instanceof String[] enumValue && enumValue.length == 2) {
            return enumValue[1];
        }
        throw new IllegalStateException("@Patch must name its group");
    }

    /**
     * Writes {@link AnchorFile#RESOURCE} for the build. Arguments: the output file, the quarantine config, the
     * Celeritas jar the build compiles against, the upstream commit, the version, the accepted SHA-256s (comma-separated),
     * then the directories of Demonica's compiled classes.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 7) {
            throw new IllegalArgumentException("usage: AnchorExtractor <out> <config> <celeritas jar> <commit> <version> <sha256,...> <classes dir>...");
        }
        Path out = Path.of(args[0]);
        List<String> mixins;
        try (Reader config = Files.newBufferedReader(Path.of(args[1]), StandardCharsets.UTF_8)) {
            mixins = configMixins(config);
        }
        ClassIndex celeritas = ClassIndex.ofJar(Path.of(args[2]));
        List<Path> classes = Arrays.stream(args, 6, args.length).map(Path::of).toList();
        List<Anchor> anchors = extract(mixins, ClassIndex.ofDirectories(classes), celeritas);
        Set<String> pins = new LinkedHashSet<>();
        for (String pin : args[5].split(",")) {
            if (!pin.isBlank()) {
                pins.add(pin.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        Files.createDirectories(out.getParent());
        try (Writer writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            AnchorFile.write(writer, new AnchorFile.Contents(args[3], args[4], pins, anchors));
        }
    }
}
