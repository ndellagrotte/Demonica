package com.demonica.diagnostics.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InheritedMemberCheckTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void flagsMinecraftMembersReachedThroughRootClasses() throws IOException {
        Path root = temporaryDirectory.resolve("root");
        Path diagnostics = temporaryDirectory.resolve("diagnostics");
        // A root class extending a Minecraft class, with one method of its own.
        write(root, "r/Screen", "net/minecraft/client/gui/GuiScreen", "own");
        // A root class that reaches no Minecraft class.
        write(root, "r/Plain", "java/lang/Object", "own");

        write(diagnostics, "d/User", "java/lang/Object", null,
            new String[] {"r/Screen", "initGui"}, new String[] {"r/Screen", "own"}, new String[] {"r/Plain", "hashCode"});
        write(diagnostics, "d/Sub", "r/Screen", null);

        List<String> problems = InheritedMemberCheck.find(root, List.of(diagnostics));

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("r/Screen.initGui")), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.startsWith("d/Sub extends r/Screen")), problems.toString());
    }

    private static void write(Path directory, String name, String superName, String ownMethod, String[]... calls)
        throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, null);
        if (ownMethod != null) {
            MethodVisitor own = writer.visitMethod(Opcodes.ACC_PUBLIC, ownMethod, "()V", null, null);
            own.visitCode();
            own.visitInsn(Opcodes.RETURN);
            own.visitMaxs(0, 0);
            own.visitEnd();
        }
        MethodVisitor caller = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
        caller.visitCode();
        for (String[] call : calls) {
            caller.visitInsn(Opcodes.ACONST_NULL);
            caller.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call[0], call[1], call[1].equals("hashCode") ? "()I" : "()V",
                false);
            if (call[1].equals("hashCode")) {
                caller.visitInsn(Opcodes.POP);
            }
        }
        caller.visitInsn(Opcodes.RETURN);
        caller.visitMaxs(0, 0);
        caller.visitEnd();
        writer.visitEnd();
        Path file = directory.resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }
}
