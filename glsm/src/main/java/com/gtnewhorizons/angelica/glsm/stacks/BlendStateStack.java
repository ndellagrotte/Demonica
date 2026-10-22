package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Setter;

public class BlendStateStack extends BlendState implements IStateStack<BlendStateStack> {

    protected final BlendState[] stack;

    protected int pointer;
    @Setter private VanillaStateLayer<BlendState> vanillaLayer;
    /**
     * Set after an indexed blend function call: GL then blends that draw buffer with a function the cached global
     * function does not describe, so a non-indexed call must not be skipped because it matches the cache. Mirrors
     * {@link com.gtnewhorizons.angelica.glsm.states.BooleanState#setUnknownState()} for the enable flag.
     */
    private boolean funcUnknown;

    public BlendStateStack() {
        stack = new BlendState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new BlendState();
        }
    }

    public BlendStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        VanillaStateLayer.capture(vanillaLayer, stack[pointer++].set(this));
        return this;
    }

    public BlendStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final BlendState saved = stack[--pointer];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptFunc(saved);
        } else {
            set(saved);
        }
        return this;
    }

    /** Copies the effective blend state, including the deferred vanilla function when held. */
    public BlendState readEffective(BlendState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    /** Marks the cached function stale for at least one draw buffer (see {@link #funcUnknown}). */
    public void setFuncUnknown() {
        funcUnknown = true;
    }

    /** Called once a non-indexed blend function reached the backend, which sets every draw buffer to it. */
    public void clearFuncUnknown() {
        funcUnknown = false;
    }

    /** Returns whether a non-indexed blend function call with these factors has to reach the backend. */
    public boolean needsFuncUpdate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        return funcUnknown
            || this.srcRgb != srcRgb
            || this.dstRgb != dstRgb
            || this.srcAlpha != srcAlpha
            || this.dstAlpha != dstAlpha;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }

    public boolean isEnabled() {
        return GLStateManager.getBlendMode().isEnabled();
    }
}
