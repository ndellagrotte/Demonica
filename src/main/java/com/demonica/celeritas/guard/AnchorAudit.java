package com.demonica.celeritas.guard;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the quarantine's anchors against the classes of a Celeritas jar, from their bytes: nothing is loaded, and a
 * class that is missing or cannot be read fails the anchors on it instead of throwing.
 *
 * <p>Anchor strings that a mixin annotation wrote are looked up in the refmap first, as Mixin does. In the distributed
 * jar that maps vanilla members to their production names; in the dev workspace there is no refmap and the names are
 * already right. Members found through bytecode or {@link Patch#context} are Celeritas's own and never renamed. A member
 * reached through a Celeritas class but declared by a vanilla or JDK supertype cannot be checked here, and passes.
 */
public final class AnchorAudit {
    private static final Set<String> OBJECT_METHODS = Set.of(
        "equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;", "getClass()Ljava/lang/Class;",
        "notify()V", "notifyAll()V", "wait()V", "wait(J)V", "wait(JI)V", "clone()Ljava/lang/Object;", "finalize()V");

    /** The bytes of classes by internal name. */
    @FunctionalInterface
    public interface ClassSource {
        /** Returns null if there is no such class. */
        byte @Nullable [] bytes(String internalName) throws IOException;
    }

    /** An anchor that does not hold, and why. */
    public record Failure(Anchor anchor, String problem) {
        @Override
        public String toString() {
            return this.anchor.patches() + " (" + Anchor.simpleName(this.anchor.mixin().replace('.', '/')) + "): "
                + this.anchor.describe() + ": " + this.problem;
        }
    }

    private record Loaded(@Nullable ClassNode node, @Nullable String problem) {
    }

    private enum Resolution { FOUND, MISSING, UNCHECKABLE }

    private final ClassSource classes;
    private final Map<String, Map<String, String>> refmap;
    private final Map<String, Loaded> loaded = new HashMap<>();

    /**
     * @param refmap the quarantine's refmap: for each mixin (internal name), annotation strings and what they map to
     */
    public AnchorAudit(ClassSource classes, Map<String, Map<String, String>> refmap) {
        this.classes = classes;
        this.refmap = refmap;
    }

    /** Whether a class belongs to the Celeritas jar: upstream's mod and the {@code common} code it shades. */
    public static boolean isCeleritas(String internalName) {
        return internalName.startsWith("org/taumc/") || internalName.startsWith("org/embeddedt/");
    }

    /** The anchors that do not hold, in the order given. Never throws for a broken or missing class. */
    public List<Failure> audit(List<Anchor> anchors) {
        List<Failure> failures = new ArrayList<>();
        for (Anchor anchor : anchors) {
            String problem;
            try {
                problem = check(anchor);
            } catch (RuntimeException | LinkageError e) {
                problem = "could not be checked (" + e + ")";
            }
            if (problem != null) {
                failures.add(new Failure(anchor, problem));
            }
        }
        return failures;
    }

    private @Nullable String check(Anchor anchor) {
        Loaded owner = load(anchor.owner());
        if (owner.node() == null) {
            return owner.problem();
        }
        ClassNode node = owner.node();
        MemberRef member = MemberRef.parse(remap(anchor, anchor.member()));
        return switch (anchor.kind()) {
            case CLASS -> null;
            case METHOD -> selected(node, member).isEmpty() ? "method missing" : null;
            case FIELD -> declaresField(node, member) ? null : "field missing";
            case MEMBER -> resolve(anchor.owner(), member) == Resolution.MISSING ? (member.field() ? "field missing" : "method missing") : null;
            case INHERITED -> declaresMethod(node, member) ? "now declared by " + Anchor.simpleName(anchor.owner()) + " itself"
                : overridable(node, member);
            case ABSENT -> declaresMethod(node, member) ? "now declared, which bypasses the patch" : null;
            case INVOKE, ACCESS -> {
                List<MethodNode> methods = selected(node, member);
                if (methods.isEmpty()) {
                    yield "method missing";
                }
                MemberRef target = MemberRef.parse(remap(anchor, anchor.target()));
                yield references(methods, target, anchor.kind() == Anchor.Kind.ACCESS) ? null
                    : anchor.kind() == Anchor.Kind.ACCESS ? "field access missing" : "call missing";
            }
        };
    }

    private String remap(Anchor anchor, String reference) {
        if (reference.isEmpty()) {
            return reference;
        }
        Map<String, String> mappings = this.refmap.get(anchor.mixin().replace('.', '/'));
        return mappings != null ? mappings.getOrDefault(reference, reference) : reference;
    }

    private Loaded load(String internalName) {
        return this.loaded.computeIfAbsent(internalName, name -> {
            byte[] bytes;
            try {
                bytes = this.classes.bytes(name);
            } catch (IOException | RuntimeException e) {
                return new Loaded(null, "class unreadable (" + e + ")");
            }
            if (bytes == null) {
                return new Loaded(null, "class missing");
            }
            try {
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                return new Loaded(node, null);
            } catch (RuntimeException e) {
                return new Loaded(null, "class unreadable (" + e + ")");
            }
        });
    }

    private static List<MethodNode> selected(ClassNode owner, MemberRef selector) {
        List<MethodNode> methods = new ArrayList<>();
        for (MethodNode method : owner.methods) {
            if (selector.selects(method.name, method.desc)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static boolean declaresMethod(ClassNode owner, MemberRef member) {
        return !member.field() && !selected(owner, member).isEmpty();
    }

    private static boolean declaresField(ClassNode owner, MemberRef member) {
        for (FieldNode field : owner.fields) {
            if (member.selects(field.name, field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean declares(ClassNode owner, MemberRef member) {
        return member.field() ? declaresField(owner, member) : declaresMethod(owner, member);
    }

    /**
     * Looks for the member in the class and its supertypes, as the JVM resolves it. Only Celeritas's classes can be
     * read; if the member is not found and a vanilla or JDK type is among the supertypes, that type may declare it, and
     * the anchor cannot be checked here.
     */
    private Resolution resolve(String owner, MemberRef member) {
        if (!member.field() && OBJECT_METHODS.contains(member.name() + member.desc())) {
            return Resolution.FOUND;
        }
        boolean uncheckable = false;
        Deque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            String type = pending.removeFirst();
            if (!seen.add(type)) {
                continue;
            }
            if (!isCeleritas(type)) {
                uncheckable |= !type.equals("java/lang/Object");
                continue;
            }
            ClassNode node = load(type).node();
            if (node == null) {
                continue;
            }
            if (declares(node, member)) {
                return Resolution.FOUND;
            }
            // Constructors are never inherited.
            if (member.name().equals("<init>")) {
                break;
            }
            if (node.superName != null) {
                pending.add(node.superName);
            }
            pending.addAll(node.interfaces);
        }
        return uncheckable ? Resolution.UNCHECKABLE : Resolution.MISSING;
    }

    /** Why the method a patch adds to {@code owner} would not override a supertype's, or null if it does. */
    private @Nullable String overridable(ClassNode owner, MemberRef member) {
        boolean uncheckable = false;
        Deque<String> pending = new ArrayDeque<>(supertypes(owner));
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String type = pending.removeFirst();
            if (!seen.add(type)) {
                continue;
            }
            if (!isCeleritas(type)) {
                uncheckable |= !type.equals("java/lang/Object");
                continue;
            }
            ClassNode node = load(type).node();
            if (node == null) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (member.selects(method.name, method.desc)) {
                    return (method.access & Opcodes.ACC_FINAL) != 0 ? "final in " + Anchor.simpleName(type) : null;
                }
            }
            pending.addAll(supertypes(node));
        }
        return uncheckable ? null : "missing from its supertypes";
    }

    private static List<String> supertypes(ClassNode node) {
        List<String> supertypes = new ArrayList<>();
        if (node.superName != null) {
            supertypes.add(node.superName);
        }
        supertypes.addAll(node.interfaces);
        return supertypes;
    }

    private static boolean references(List<MethodNode> methods, MemberRef target, boolean field) {
        for (MethodNode method : methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (field && insn instanceof FieldInsnNode access && target.isReferencedBy(access.owner, access.name, access.desc)) {
                    return true;
                }
                if (!field && insn instanceof MethodInsnNode call && target.isReferencedBy(call.owner, call.name, call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
