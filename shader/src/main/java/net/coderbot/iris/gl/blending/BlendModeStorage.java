package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaBooleanLayer;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Getter;

import java.util.Objects;

/**
 * Holds the blend state a shader pass imposes ({@code blend.<program>} and {@code blend.<program>.<buffer>}) against
 * the application's own blend calls.
 * <p>
 * While an override is held, GLSM routes vanilla {@code glEnable/glDisable(GL_BLEND)} and blend function calls here
 * ({@link #isBlendLocked()}): they are recorded as the vanilla state ({@link #ENABLE_LAYER}, {@link #FUNC_LAYER}) and
 * reach GL later. What "later" means depends on the override:
 * <ul>
 * <li>A global override owns the non-indexed blend state, so vanilla changes wait until {@link #restoreBlend()}.</li>
 * <li>Per-buffer overrides own only their draw buffers. Vanilla changes still reach GL at the next draw through
 * {@link #flushDeferredBlend()}, which then re-applies the per-buffer overrides, because a non-indexed
 * {@code glEnable(GL_BLEND)} or {@code glBlendFuncSeparate} resets every buffer's indexed state. The pass is bound
 * before vanilla sets up its blending (the glint and particle setup runs after the pass switch), so holding those
 * calls back until the pass ends drew glint and additive effects with stale state, usually with blending off.</li>
 * </ul>
 */
public class BlendModeStorage {
	/** Iris's colour attachments; the draw-buffer index of a per-buffer override is below this. */
	private static final int MAX_DRAW_BUFFERS = 16;

	private static boolean originalBlendEnable;
	private static final BlendState originalBlend = new BlendState();
	@Getter private static boolean blendLocked;
	@Getter private static boolean hasDeferredChanges;
	@Getter private static boolean overrideHeld;
	private static boolean hasGlobalOverride;
	// The per-buffer overrides currently held, keyed by draw-buffer index, so the flush can re-apply them. A bit in
	// bufferOverrideOffMask means "blending off" for that buffer; otherwise bufferOverrideStates holds its function.
	private static int bufferOverrideMask;
	private static int bufferOverrideOffMask;
	private static final BlendState[] bufferOverrideStates = new BlendState[MAX_DRAW_BUFFERS];
	private static final BlendStateAccess DEFAULT_STATE_ACCESS = new BlendStateAccessImpl();
	private static BlendStateAccess stateAccess = DEFAULT_STATE_ACCESS;

	static {
		for (int i = 0; i < MAX_DRAW_BUFFERS; i++) {
			bufferOverrideStates[i] = new BlendState();
		}
	}

	public static void overrideBlend(BlendState override) {
		captureVanilla();

		overrideHeld = true;
		hasGlobalOverride = true;
		// The non-indexed calls below reset every buffer's indexed state, so earlier buffer overrides are gone.
		clearBufferOverrides();
		blendLocked = false;
		try {
			if (override == null) {
				stateAccess.setBlendEnabled(false);
			} else {
				stateAccess.setBlendEnabled(true);
				stateAccess.setBlendFunction(override);
			}
		} finally {
			blendLocked = true;
		}
	}

	public static void overrideBufferBlend(int index, BlendState override) {
		captureVanilla();

		applyBufferOverride(index, override);
		// Passes are re-used and Celeritas's terrain applies its buffer overrides without a restore in between, so a
		// later override of the same buffer replaces the stored one.
		if (index >= 0 && index < MAX_DRAW_BUFFERS) {
			final int bit = 1 << index;
			bufferOverrideMask |= bit;
			if (override == null) {
				bufferOverrideOffMask |= bit;
			} else {
				bufferOverrideOffMask &= ~bit;
				bufferOverrideStates[index].setAll(
					override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha()
				);
			}
		}

		blendLocked = true;
		overrideHeld = true;
	}

	public static void deferBlendModeToggle(boolean enabled) {
		prepareDeferredState();
		originalBlendEnable = enabled;
		hasDeferredChanges = true;
	}

	public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		prepareDeferredState();
        originalBlend.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
		hasDeferredChanges = true;
	}

	/**
	 * Applies deferred vanilla blend changes before a draw, unless a global override holds the non-indexed state.
	 * Under per-buffer overrides the vanilla enable and function go to GL and the buffer overrides are re-applied on
	 * top. {@link #isOverrideHeld()} stays true throughout: the vanilla value GLSM reports does not move, so no
	 * VANILLA_BLEND_CHANGE (and no pass re-match) starts from inside the pre-draw.
	 */
	public static void flushDeferredBlend() {
		if (!hasDeferredChanges || hasGlobalOverride) {
			return;
		}

		final boolean locked = blendLocked;
		blendLocked = false;
		try {
			applyOriginalBlend();
		} finally {
			blendLocked = locked;
		}
		hasDeferredChanges = false;

		if (locked) {
			reapplyBufferOverrides();
		}
	}

	public static void restoreBlend() {
		if (!overrideHeld) {
			return;
		}

		hasGlobalOverride = false;
		blendLocked = false;
		clearBufferOverrides();
		applyOriginalBlend();
		hasDeferredChanges = false;
		overrideHeld = false;
	}

	private static void prepareDeferredState() {
		if (!blendLocked && !hasDeferredChanges) {
			saveCurrentBlend();
		}
	}

	private static void saveCurrentBlend() {
		originalBlendEnable = stateAccess.isBlendEnabled();
		stateAccess.copyBlendState(originalBlend);
	}

	private static void captureVanilla() {
		if (!overrideHeld) {
			saveCurrentBlend();
		}
	}

	private static void applyOriginalBlend() {
		stateAccess.setBlendEnabled(originalBlendEnable);
		stateAccess.setBlendFunction(originalBlend);
	}

	private static void applyBufferOverride(int index, BlendState override) {
		if (override == null) {
			stateAccess.setBufferBlendEnabled(index, false);
		} else {
			stateAccess.setBufferBlendEnabled(index, true);
			stateAccess.setBufferBlendFunction(index, override);
		}
	}

	private static void reapplyBufferOverrides() {
		int mask = bufferOverrideMask;
		while (mask != 0) {
			final int index = Integer.numberOfTrailingZeros(mask);
			mask &= mask - 1;
			applyBufferOverride(index, (bufferOverrideOffMask & (1 << index)) != 0 ? null : bufferOverrideStates[index]);
		}
	}

	private static void clearBufferOverrides() {
		bufferOverrideMask = 0;
		bufferOverrideOffMask = 0;
	}

	static void setBlendStateAccessForTesting(BlendStateAccess access) {
		stateAccess = Objects.requireNonNull(access, "access");
		resetStorageState();
		saveCurrentBlend();
	}

	static void restoreDefaultBlendStateAccessForTesting() {
		stateAccess = DEFAULT_STATE_ACCESS;
		resetStorageState();
	}

	private static void resetStorageState() {
		blendLocked = false;
		hasDeferredChanges = false;
		overrideHeld = false;
		hasGlobalOverride = false;
		clearBufferOverrides();
	}

	/** Exposes the deferred vanilla enable state to GLSM without replacing the shader override. */
	public static final VanillaBooleanLayer ENABLE_LAYER = new VanillaBooleanLayer() {
		@Override
		public boolean isOverrideHeld() {
			return overrideHeld;
		}

		@Override
		public boolean getVanilla() {
			return originalBlendEnable;
		}

		@Override
		public void setVanilla(boolean enabled) {
			deferBlendModeToggle(enabled);
		}
	};

	/** Exposes the deferred vanilla blend function to GLSM without replacing the shader override. */
	public static final VanillaStateLayer<BlendState> FUNC_LAYER = new VanillaStateLayer<>() {
		@Override
		public boolean isOverrideHeld() {
			return overrideHeld;
		}

		@Override
		public void readVanilla(BlendState into) {
			into.set(originalBlend);
		}

		@Override
		public void writeVanilla(BlendState from) {
			deferBlendFunc(from.getSrcRgb(), from.getDstRgb(), from.getSrcAlpha(), from.getDstAlpha());
		}
	};

	/**
	 * Provides the concrete blend state operations used by the override state machine.
	 */
	interface BlendStateAccess {
		/** Returns the actual global blend enable state. */
		boolean isBlendEnabled();

		/** Copies the actual global blend function into {@code destination}. */
		void copyBlendState(BlendState destination);

		/** Applies the actual global blend enable state. */
		void setBlendEnabled(boolean enabled);

		/** Applies the actual global blend function. */
		void setBlendFunction(BlendState state);

		/** Applies an indexed blend enable override. */
		void setBufferBlendEnabled(int index, boolean enabled);

		/** Applies an indexed blend function override. */
		void setBufferBlendFunction(int index, BlendState state);
	}

	private static final class BlendStateAccessImpl implements BlendStateAccess {
		@Override
		public boolean isBlendEnabled() {
			return GLStateManager.getBlendMode().isEnabled();
		}

		@Override
		public void copyBlendState(BlendState destination) {
			destination.set(GLStateManager.getBlendState());
		}

		@Override
		public void setBlendEnabled(boolean enabled) {
			if (enabled) {
				GLStateManager.enableBlend();
			} else {
				GLStateManager.disableBlend();
			}
		}

		@Override
		public void setBlendFunction(BlendState state) {
			GLStateManager.tryBlendFuncSeparate(
				state.getSrcRgb(), state.getDstRgb(), state.getSrcAlpha(), state.getDstAlpha()
			);
		}

		@Override
		public void setBufferBlendEnabled(int index, boolean enabled) {
			if (enabled) {
				RenderSystem.enableBufferBlend(index);
			} else {
				RenderSystem.disableBufferBlend(index);
			}
		}

		@Override
		public void setBufferBlendFunction(int index, BlendState state) {
			RenderSystem.blendFuncSeparatei(
				index, state.getSrcRgb(), state.getDstRgb(), state.getSrcAlpha(), state.getDstAlpha()
			);
		}
	}
}
