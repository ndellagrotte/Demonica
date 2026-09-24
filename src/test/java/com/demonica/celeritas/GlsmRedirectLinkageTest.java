package com.demonica.celeritas;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GLSM's redirector rewrites GL calls by owner prefix and method name while keeping the caller's descriptor,
 * so every call it rewrites in Celeritas must find a {@code GLStateManager} overload with exactly that
 * descriptor. A missing overload is a {@code NoSuchMethodError} the first time Celeritas draws. This runs the
 * real redirector over every class in the pinned Celeritas jar and checks each rewritten call against the
 * compiled {@code GLStateManager} (which cannot be loaded without a GL context).
 */
class GlsmRedirectLinkageTest {
    private static final String GL_STATE_MANAGER = "com/gtnewhorizons/angelica/glsm/GLStateManager";

    @Test
    void everyRedirectedCeleritasCallLinks() throws IOException {
        Set<String> glsmMethods = declaredMethods(GL_STATE_MANAGER);
        GLSMRedirector redirector = new GLSMRedirector();
        CeleritasJar jar = CeleritasJar.get();

        Set<String> unresolved = new TreeSet<>();
        Set<String> missingTypes = new TreeSet<>();
        int rewrittenClasses = 0;

        for (String className : jar.classNames()) {
            byte[] bytes = jar.bytes(className);
            if (!redirector.shouldTransform(bytes)) {
                continue;
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            if (!redirector.transformClassNode(className.replace('/', '.'), node)) {
                continue;
            }
            rewrittenClasses++;

            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.equals(GL_STATE_MANAGER)) {
                        if (!glsmMethods.contains(call.name + call.desc)) {
                            unresolved.add(call.name + call.desc + " (from " + className + "." + method.name + ")");
                        }
                    } else if (insn instanceof InvokeDynamicInsnNode indy) {
                        for (Object arg : indy.bsmArgs) {
                            if (arg instanceof Handle handle && handle.getOwner().equals(GL_STATE_MANAGER)
                                && !glsmMethods.contains(handle.getName() + handle.getDesc())) {
                                unresolved.add(handle.getName() + handle.getDesc() + " (method reference in " + className + ")");
                            }
                        }
                    } else if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                        && type.desc.startsWith("com/gtnewhorizons/angelica/glsm/") && !classExists(type.desc)) {
                        missingTypes.add(type.desc + " (from " + className + ")");
                    }
                }
            }
        }

        assertTrue(rewrittenClasses > 0, "the redirector rewrote no Celeritas class; the jar or the redirector changed shape");
        assertTrue(unresolved.isEmpty(), "GLStateManager lacks the overloads the redirector produces for Celeritas:\n  "
            + String.join("\n  ", unresolved));
        assertTrue(missingTypes.isEmpty(), "redirected types do not exist:\n  " + String.join("\n  ", missingTypes));
    }

    @Test
    void celeritasJarIsTheDevRemap() {
        // The dev remap renames SRG members to MCP; the anchors and the linkage above are checked in MCP names.
        ClassNode renderGlobalMixin = CeleritasJar.get().node("org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin");
        Set<String> names = new HashSet<>();
        renderGlobalMixin.methods.forEach(m -> names.add(m.name));
        assertTrue(names.contains("renderBlockLayer"), "RenderGlobalMixin should carry the MCP name renderBlockLayer");
        assertFalse(names.contains("func_174977_a"), "RenderGlobalMixin still carries the SRG name func_174977_a");
    }

    private static Set<String> declaredMethods(String internalName) throws IOException {
        Set<String> methods = new HashSet<>();
        String current = internalName;
        while (current != null && !current.equals("java/lang/Object")) {
            ClassNode node = readClasspathClass(current);
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_STATIC) != 0) {
                    methods.add(method.name + method.desc);
                }
            }
            current = node.superName;
        }
        return methods;
    }

    private static boolean classExists(String internalName) {
        return GlsmRedirectLinkageTest.class.getClassLoader().getResource(internalName + ".class") != null;
    }

    private static ClassNode readClasspathClass(String internalName) throws IOException {
        ClassNode node = new ClassNode();
        try (InputStream in = GlsmRedirectLinkageTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                throw new IOException(internalName + ".class is not on the test classpath");
            }
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
        }
        return node;
    }
}
