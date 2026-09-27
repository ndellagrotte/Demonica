package com.demonica.diagnostics.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * verifyDiagnosticsRemap: finds every field or method instruction in the diagnostics classes whose owner is a root
 * project class but whose member is not declared by a root class and is reached through a Minecraft class. The
 * diagnostics jar's remap does not see the root classes, so such a reference would keep its MCP name.
 *
 * <p>Arguments: the root project's classes directory, then the diagnostics classes directories.
 */
public final class InheritedMemberCheck {
    private final Map<String, ClassNode> rootClasses = new HashMap<>();

    private InheritedMemberCheck() {
    }

    public static void main(String[] args) throws IOException {
        InheritedMemberCheck check = new InheritedMemberCheck();
        check.readRoot(Path.of(args[0]));
        List<String> problems = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path directory = Path.of(args[i]);
            if (Files.isDirectory(directory)) {
                check.scan(directory, problems);
            }
        }
        if (!problems.isEmpty()) {
            System.err.println("The diagnostics reach Minecraft members through root-project classes; the remap would "
                + "leave these in MCP names. Call them through a Minecraft-typed reference:");
            problems.forEach(problem -> System.err.println(" - " + problem));
            System.exit(1);
        }
        System.out.println("No diagnostics class reaches a Minecraft member through a root-project class");
    }

    private void readRoot(Path directory) throws IOException {
        for (Path file : classFiles(directory)) {
            ClassNode node = read(file);
            rootClasses.put(node.name, node);
        }
    }

    private void scan(Path directory, List<String> problems) throws IOException {
        for (Path file : classFiles(directory)) {
            ClassNode node = read(file);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    String owner;
                    String name;
                    String descriptor;
                    boolean isField;
                    if (instruction instanceof MethodInsnNode call) {
                        owner = call.owner;
                        name = call.name;
                        descriptor = call.desc;
                        isField = false;
                    } else if (instruction instanceof FieldInsnNode field) {
                        owner = field.owner;
                        name = field.name;
                        descriptor = field.desc;
                        isField = true;
                    } else {
                        continue;
                    }
                    if (rootClasses.containsKey(owner) && inheritedFromMinecraft(owner, name, descriptor, isField)) {
                        problems.add(node.name + "." + method.name + " uses " + owner + "." + name + descriptor);
                    }
                }
            }
        }
    }

    /** Whether no root class in owner's hierarchy declares the member while a Minecraft class is among its supertypes. */
    private boolean inheritedFromMinecraft(String owner, String name, String descriptor, boolean isField) {
        Deque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(owner);
        boolean reachesMinecraft = false;
        while (!pending.isEmpty()) {
            String type = pending.poll();
            if (!seen.add(type)) {
                continue;
            }
            ClassNode node = rootClasses.get(type);
            if (node == null) {
                reachesMinecraft |= type.startsWith("net/minecraft/");
                continue;
            }
            if (declares(node, name, descriptor, isField)) {
                return false;
            }
            if (node.superName != null) {
                pending.add(node.superName);
            }
            pending.addAll(node.interfaces);
        }
        return reachesMinecraft;
    }

    private static boolean declares(ClassNode node, String name, String descriptor, boolean isField) {
        if (isField) {
            for (FieldNode field : node.fields) {
                if (field.name.equals(name) && field.desc.equals(descriptor)) {
                    return true;
                }
            }
            return false;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> classFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".class")).toList();
        }
    }

    private static ClassNode read(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
