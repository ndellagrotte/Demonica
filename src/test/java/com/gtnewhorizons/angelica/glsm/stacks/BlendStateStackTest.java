package com.gtnewhorizons.angelica.glsm.stacks;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dedupe GLStateManager.glBlendFunc and tryBlendFuncSeparate apply to non-indexed blend function calls. After Iris
 * sets a per-buffer function (RenderSystem.blendFuncSeparatei), restoring the vanilla function with the cached factors
 * must still reach GL, or that buffer keeps the pass's function.
 * <p>
 * The condition itself is tested on BlendStateStack. GLStateManager and RenderSystem cannot be loaded here because
 * their static initializers need a live GL context, so their use of it is asserted against the compiled class bytes
 * instead (same pattern as GLStateManagerRedirectContractTest).
 */
class BlendStateStackTest {
    private static final String BLEND_STATE_STACK = "com/gtnewhorizons/angelica/glsm/stacks/BlendStateStack";
    private static final String GL_STATE_MANAGER = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String RENDER_SYSTEM = "com/gtnewhorizons/angelica/glsm/RenderSystem";

    @Test
    void matchingFunctionIsSkippedWhileTheCacheIsKnown() {
        BlendStateStack state = new BlendStateStack();
        state.setAll(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        assertFalse(state.needsFuncUpdate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO));
        assertTrue(state.needsFuncUpdate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO));
        assertTrue(state.needsFuncUpdate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE));
    }

    @Test
    void indexedFunctionForcesTheNextMatchingCallThrough() {
        BlendStateStack state = new BlendStateStack();
        state.setAll(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        state.setFuncUnknown();

        assertTrue(state.needsFuncUpdate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO));

        state.clearFuncUnknown();

        assertFalse(state.needsFuncUpdate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO));
    }

    @Test
    void indexedBlendFunctionMarksTheCachedFunctionUnknown() throws IOException {
        assertTrue(
            invokes(RENDER_SYSTEM, "blendFuncSeparatei", "(IIIII)V", "setFuncUnknown"),
            "RenderSystem.blendFuncSeparatei must mark GLSM's cached blend function unknown, or the restore of the "
                + "vanilla function after a per-buffer override is deduplicated away"
        );
    }

    @Test
    void nonIndexedBlendFunctionsConsultAndClearTheUnknownFlag() throws IOException {
        for (String[] method : new String[][] {{"glBlendFunc", "(II)V"}, {"tryBlendFuncSeparate", "(IIII)V"}}) {
            assertTrue(
                invokes(GL_STATE_MANAGER, method[0], method[1], "needsFuncUpdate"),
                "GLStateManager." + method[0] + " must dedupe through BlendStateStack.needsFuncUpdate so an unknown "
                    + "function is not skipped"
            );
            assertTrue(
                invokes(GL_STATE_MANAGER, method[0], method[1], "clearFuncUnknown"),
                "GLStateManager." + method[0] + " must clear the unknown flag once its call reaches the backend"
            );
        }
    }

    private static boolean invokes(String owner, String methodName, String descriptor, String calledName)
        throws IOException {
        for (MethodNode method : loadClassNode(owner).methods) {
            if (!method.name.equals(methodName) || !method.desc.equals(descriptor)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(BLEND_STATE_STACK)
                    && call.name.equals(calledName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ClassNode loadClassNode(String internalName) throws IOException {
        String file = internalName + ".class";
        ClassNode classNode = new ClassNode();
        try (InputStream in = BlendStateStackTest.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new IOException("Could not find " + file + " on the test classpath");
            }
            new ClassReader(in).accept(classNode, 0);
        }
        return classNode;
    }
}
