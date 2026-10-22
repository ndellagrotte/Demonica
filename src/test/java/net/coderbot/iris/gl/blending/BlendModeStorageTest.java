package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.BlendState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlendModeStorageTest {
    private FakeBlendStateAccess stateAccess;

    @BeforeEach
    void setUp() {
        stateAccess = new FakeBlendStateAccess();
        stateAccess.enabled = true;
        stateAccess.blend.setAll(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.setBlendStateAccessForTesting(stateAccess);
    }

    @AfterEach
    void tearDown() {
        BlendModeStorage.restoreDefaultBlendStateAccessForTesting();
    }

    @Test
    void deferredVanillaBlendDoesNotEscapeGlobalOverride() {
        BlendModeOverride.OFF.apply();
        assertFalse(stateAccess.enabled);

        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.flushDeferredBlend();

        assertFalse(stateAccess.enabled);
        assertEquals(GL11.GL_SRC_ALPHA, stateAccess.blend.getSrcRgb());
        assertTrue(BlendModeStorage.isBlendLocked());
        assertTrue(BlendModeStorage.isHasDeferredChanges());

        BlendModeOverride.restore();

        assertTrue(stateAccess.enabled);
        assertEquals(GL11.GL_ONE, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ZERO, stateAccess.blend.getDstRgb());
        assertFalse(BlendModeStorage.isBlendLocked());
        assertFalse(BlendModeStorage.isHasDeferredChanges());
    }

    @Test
    void deferredBlendFlushesNormallyWithoutOverride() {
        stateAccess.enabled = false;
        BlendModeStorage.setBlendStateAccessForTesting(stateAccess);

        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BlendModeStorage.flushDeferredBlend();

        assertTrue(stateAccess.enabled);
        assertEquals(GL11.GL_ONE, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ONE, stateAccess.blend.getDstRgb());
        assertEquals(GL11.GL_SRC_ALPHA, stateAccess.blend.getSrcAlpha());
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, stateAccess.blend.getDstAlpha());
        assertFalse(BlendModeStorage.isHasDeferredChanges());
    }

    @Test
    void consecutiveGlobalOverridePreservesDeferredVanillaState() {
        BlendModeOverride.OFF.apply();
        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);

        BlendState secondOverride = new BlendState(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        new BlendModeOverride(secondOverride).apply();
        BlendModeStorage.flushDeferredBlend();

        assertTrue(stateAccess.enabled);
        assertEquals(GL11.GL_SRC_ALPHA, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, stateAccess.blend.getDstRgb());
        assertTrue(BlendModeStorage.isBlendLocked());
        assertTrue(BlendModeStorage.isHasDeferredChanges());

        BlendModeOverride.restore();

        assertTrue(stateAccess.enabled);
        assertEquals(GL11.GL_ONE, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ZERO, stateAccess.blend.getDstRgb());
        assertFalse(BlendModeStorage.isBlendLocked());
        assertFalse(BlendModeStorage.isHasDeferredChanges());
    }

    @Test
    void bufferOverrideLetsDeferredVanillaBlendThroughAndKeepsTheOverride() {
        int index = 3;
        BlendState indexedOverride = new BlendState(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        BlendModeStorage.overrideBufferBlend(index, indexedOverride);
        BlendModeStorage.deferBlendModeToggle(false);
        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);

        BlendModeStorage.flushDeferredBlend();

        assertFalse(stateAccess.enabled);
        assertEquals(GL11.GL_ONE, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ONE, stateAccess.blend.getDstRgb());
        assertTrue(stateAccess.isBufferEnabled(index));
        assertEquals(GL11.GL_SRC_ALPHA, stateAccess.bufferBlend(index).getSrcRgb());
        assertEquals(GL11.GL_ONE, stateAccess.bufferBlend(index).getDstRgb());
        assertTrue(BlendModeStorage.isBlendLocked());
        assertTrue(BlendModeStorage.isOverrideHeld());
        assertFalse(BlendModeStorage.isHasDeferredChanges());
        // GLSM keeps reading the vanilla values while the override is held.
        assertFalse(BlendModeStorage.ENABLE_LAYER.getVanilla());
        BlendState vanilla = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanilla);
        assertEquals(GL11.GL_ONE, vanilla.getSrcRgb());
        assertEquals(GL11.GL_ZERO, vanilla.getDstAlpha());

        BlendModeOverride.restore();

        assertFalse(stateAccess.enabled);
        assertEquals(GL11.GL_ONE, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ONE, stateAccess.blend.getDstRgb());
        assertFalse(stateAccess.isBufferEnabled(index));
        assertEquals(GL11.GL_ONE, stateAccess.bufferBlend(index).getSrcRgb());
        assertFalse(BlendModeStorage.isBlendLocked());
        assertFalse(BlendModeStorage.isOverrideHeld());
        assertFalse(BlendModeStorage.isHasDeferredChanges());

        // The restore dropped the stored override: a later pass's flush re-applies only its own buffers.
        BlendModeStorage.overrideBufferBlend(1, null);
        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.flushDeferredBlend();

        assertTrue(stateAccess.isBufferEnabled(index));
        assertEquals(GL11.GL_ONE, stateAccess.bufferBlend(index).getSrcRgb());
        assertFalse(stateAccess.isBufferEnabled(1));
    }

    @Test
    void globalOverrideStillDefersThroughFlushWithBufferOverrides() {
        BlendModeOverride.OFF.apply();
        BlendModeStorage.overrideBufferBlend(2, new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE));
        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);

        BlendModeStorage.flushDeferredBlend();

        assertFalse(stateAccess.enabled);
        assertEquals(GL11.GL_SRC_ALPHA, stateAccess.blend.getSrcRgb());
        assertTrue(stateAccess.isBufferEnabled(2));
        assertEquals(GL11.GL_ONE, stateAccess.bufferBlend(2).getDstRgb());
        assertTrue(BlendModeStorage.isBlendLocked());
        assertTrue(BlendModeStorage.isHasDeferredChanges());

        BlendModeOverride.restore();

        assertTrue(stateAccess.enabled);
        assertTrue(stateAccess.isBufferEnabled(2));
        assertEquals(GL11.GL_ONE, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ZERO, stateAccess.bufferBlend(2).getDstRgb());
        assertFalse(BlendModeStorage.isHasDeferredChanges());
    }

    @Test
    void offBufferOverrideIsReappliedAfterVanillaEnableBlend() {
        // EntityRenderer disables blending before the particle pass binds; the particle code enables it afterwards.
        stateAccess.enabled = false;
        BlendModeStorage.setBlendStateAccessForTesting(stateAccess);
        BlendModeStorage.overrideBufferBlend(1, null);
        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.deferBlendFunc(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        BlendModeStorage.flushDeferredBlend();

        assertTrue(stateAccess.enabled);
        assertTrue(stateAccess.isBufferEnabled(0));
        assertFalse(stateAccess.isBufferEnabled(1));
        assertTrue(stateAccess.isBufferEnabled(2));
        assertTrue(stateAccess.overrideHeldDuringGlobalCalls);
        assertTrue(BlendModeStorage.isBlendLocked());
    }

    @Test
    void functionBufferOverrideIsReappliedAfterVanillaBlendFunc() {
        BlendState additive = new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        BlendModeStorage.overrideBufferBlend(0, additive);
        // Glint sets its function after the pass binds.
        BlendModeStorage.deferBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_SRC_COLOR, GL11.GL_ONE);

        BlendModeStorage.flushDeferredBlend();

        assertEquals(GL11.GL_SRC_COLOR, stateAccess.blend.getSrcRgb());
        assertEquals(GL11.GL_ONE, stateAccess.bufferBlend(0).getSrcRgb());
        assertEquals(GL11.GL_ONE, stateAccess.bufferBlend(0).getDstAlpha());
        assertTrue(stateAccess.isBufferEnabled(0));
        assertEquals(GL11.GL_SRC_COLOR, stateAccess.bufferBlend(1).getSrcRgb());
    }

    @Test
    void laterOverrideOfTheSameBufferReplacesTheStoredOne() {
        BlendModeStorage.overrideBufferBlend(2, new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE));
        BlendModeStorage.overrideBufferBlend(2, null);
        BlendModeStorage.deferBlendModeToggle(true);

        BlendModeStorage.flushDeferredBlend();

        assertTrue(stateAccess.enabled);
        assertFalse(stateAccess.isBufferEnabled(2));

        BlendModeStorage.overrideBufferBlend(2, new BlendState(GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));
        BlendModeStorage.deferBlendModeToggle(true);
        BlendModeStorage.flushDeferredBlend();

        assertTrue(stateAccess.isBufferEnabled(2));
        assertEquals(GL11.GL_ZERO, stateAccess.bufferBlend(2).getSrcRgb());
    }

    /**
     * Models GL's indexed blend state: a non-indexed enable or function call resets every draw buffer, so buffer
     * overrides the storage does not re-apply fall back to the global state. Global calls made while the storage is
     * locked are deferred back into it, as GLSM's enableBlend/disableBlend/tryBlendFuncSeparate do, so a flush that
     * forgets to unlock never reaches the fake GL state.
     */
    private static final class FakeBlendStateAccess implements BlendModeStorage.BlendStateAccess {
        private boolean enabled;
        private final BlendState blend = new BlendState();
        private final Map<Integer, Boolean> bufferEnabled = new HashMap<>();
        private final Map<Integer, BlendState> bufferBlend = new HashMap<>();
        private boolean overrideHeldDuringGlobalCalls = true;

        boolean isBufferEnabled(int index) {
            return bufferEnabled.getOrDefault(index, enabled);
        }

        BlendState bufferBlend(int index) {
            return bufferBlend.getOrDefault(index, blend);
        }

        @Override
        public boolean isBlendEnabled() {
            return enabled;
        }

        @Override
        public void copyBlendState(BlendState destination) {
            destination.set(blend);
        }

        @Override
        public void setBlendEnabled(boolean enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(enabled);
                return;
            }
            this.enabled = enabled;
            bufferEnabled.clear();
            overrideHeldDuringGlobalCalls &= BlendModeStorage.isOverrideHeld();
        }

        @Override
        public void setBlendFunction(BlendState state) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(
                    state.getSrcRgb(), state.getDstRgb(), state.getSrcAlpha(), state.getDstAlpha()
                );
                return;
            }
            blend.set(state);
            bufferBlend.clear();
            overrideHeldDuringGlobalCalls &= BlendModeStorage.isOverrideHeld();
        }

        @Override
        public void setBufferBlendEnabled(int index, boolean enabled) {
            bufferEnabled.put(index, enabled);
        }

        @Override
        public void setBufferBlendFunction(int index, BlendState state) {
            BlendState copy = new BlendState();
            copy.set(state);
            bufferBlend.put(index, copy);
        }
    }
}
