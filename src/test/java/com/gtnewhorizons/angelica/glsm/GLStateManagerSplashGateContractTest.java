package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #57. While FML's splash runs, the splash thread owns the window context and the main thread works on a shared
 * off-screen context (Forced Resourcepacks reloads resources from preInit, which rebuilds Kirino's text runtime there).
 * GLSM's cache describes the window context only, so a method called from the non-caching thread must not write it:
 * vertex array, buffer and framebuffer names are per context, and the VAO Kirino created on the shared context took the
 * number of the VAO the splash thread was drawing with. Forgetting that number's record nulled the splash thread's
 * attribute state mid-draw and crashed both threads.
 * <p>
 * GLStateManager cannot be loaded here (its static initializer needs a live GL backend), so the gates are asserted
 * against the compiled class bytes, as BlendStateStackTest does.
 */
class GLStateManagerSplashGateContractTest {
    private static final String GL_STATE_MANAGER = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String RENDER_BACKEND = "com/gtnewhorizons/angelica/glsm/backend/RenderBackend";

    private static final String[][] GATED = {
        {"onDeleteTexture", "(I)V"},
        {"deferDeleteTexture", "(I)V"},
        {"glClientActiveTexture", "(I)V"},
        {"glBindVertexArray", "(I)V"},
        {"glDeleteVertexArrays", "(I)V"},
        {"glGenVertexArrays", "()I"},
        {"glEnableVertexAttribArray", "(I)V"},
        {"glDisableVertexAttribArray", "(I)V"},
        {"glVertexAttribPointer", "(IIIZIJ)V"},
        {"glVertexAttribIPointer", "(IIIIJ)V"},
        {"glBindBuffer", "(II)V"},
        {"invalidateDeletedBuffer", "(I)V"},
        {"glBindFramebuffer", "(II)V"},
        {"glDeleteFramebuffers", "(I)V"},
    };

    @Test
    void cacheWritesReachableDuringTheSplashConsultTheCachingGate() throws IOException {
        ClassNode glsm = loadClassNode(GL_STATE_MANAGER);
        for (String[] method : GATED) {
            assertTrue(
                invokes(glsm, method[0], method[1], GL_STATE_MANAGER, "isCachingEnabled"),
                "GLStateManager." + method[0] + method[1] + " must check isCachingEnabled: called from the off-screen "
                    + "context during the splash it would write the window context's cache (issue #57)"
            );
        }
    }

    @Test
    void theSplashHandoverReplaysTheCacheIntoTheDriver() throws IOException {
        ClassNode glsm = loadClassNode(GL_STATE_MANAGER);
        assertTrue(
            invokes(glsm, "markSplashComplete", "()V", GL_STATE_MANAGER, "replayStateToBackend"),
            "markSplashComplete must replay the cached state itself; the frame-begin hook that was meant to never ran"
        );
        for (String backendCall : new String[] {"activeTexture", "bindTexture", "isTexture"}) {
            assertTrue(
                invokes(glsm, "replayStateToBackend", "()V", RENDER_BACKEND, backendCall),
                "replayStateToBackend must push the cached texture unit bindings through RenderBackend." + backendCall
            );
        }
        assertTrue(
            readsField(glsm, "replayStateToBackend", "()V", "maxBoundTextureUnit"),
            "replayStateToBackend must cover every unit the cache knows a binding for"
        );
        assertTrue(
            readsField(glsm, "glDeleteVertexArrays", "(I)V", "offContextVertexArrays")
                && readsField(glsm, "glGenVertexArrays", "()I", "offContextVertexArrays"),
            "a VAO name generated off the window context must be remembered, so its delete after the splash does not "
                + "take the window-context VAO of the same number (GLSM's default VAO) with it"
        );
        assertFalse(
            glsm.methods.stream().anyMatch(m -> m.name.equals("takeStateSeedPending")),
            "the pending-seed flag was dead code: nothing consumed it"
        );
    }

    @Test
    void theSharedContextGetsADefaultVertexArrayOnACoreProfile() throws IOException {
        ClassNode glsm = loadClassNode(GL_STATE_MANAGER);
        assertTrue(
            invokes(glsm, "onSharedContextCurrent", "()V", RENDER_BACKEND, "genVertexArrays"),
            "onSharedContextCurrent must create the shared context's default VAO through the backend, not through "
                + "glGenVertexArrays, whose off-context bookkeeping is for names a mod may delete later (#42)"
        );
        assertTrue(
            invokes(glsm, "onSharedContextCurrent", "()V", RENDER_BACKEND, "bindVertexArray"),
            "onSharedContextCurrent must bind the shared context's default VAO (#42)"
        );
        assertTrue(
            invokes(glsm, "onSharedContextCurrent", "()V", "com/gtnewhorizons/angelica/glsm/RenderSystem", "getContextProfile"),
            "the shared context's default VAO is for the core profile only; VAO 0 is a legal vertex array on compatibility"
        );
        assertTrue(
            readsField(glsm, "glBindVertexArray", "(I)V", "offContextDefaultVAO"),
            "glBindVertexArray(0) on the shared context must bind that context's default VAO, as it does on the window (#42)"
        );
        assertTrue(
            readsField(glsm, "markSplashComplete", "()V", "offContextDefaultVAO"),
            "markSplashComplete must forget the shared context's default VAO: finish closed the context it lived on"
        );
    }

    private static boolean invokes(ClassNode owner, String methodName, String descriptor, String calledOwner, String calledName) {
        for (MethodNode method : owner.methods) {
            if (!method.name.equals(methodName) || !method.desc.equals(descriptor)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(calledOwner)
                    && call.name.equals(calledName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean readsField(ClassNode owner, String methodName, String descriptor, String fieldName) {
        for (MethodNode method : owner.methods) {
            if (!method.name.equals(methodName) || !method.desc.equals(descriptor)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.name.equals(fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ClassNode loadClassNode(String internalName) throws IOException {
        String file = internalName + ".class";
        ClassNode classNode = new ClassNode();
        try (InputStream in = GLStateManagerSplashGateContractTest.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new IOException("Could not find " + file + " on the test classpath");
            }
            new ClassReader(in).accept(classNode, 0);
        }
        return classNode;
    }
}
