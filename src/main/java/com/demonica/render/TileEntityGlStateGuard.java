package com.demonica.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Saves and restores the full GLSM-tracked GL state around Forge's tile-entity (TESR) batches.
 *
 * <p>Vanilla relies on tile-entity renderers leaving GL state as they found it, but modded
 * TESRs (notably HBM-CE machines) leak depth mask/func, blend and texture state, which then lands
 * in whatever is drawn next: the translucent terrain pass and the HUD. GLSM already maintains a
 * complete tracked-state stack ({@link GLStateManager#glPushAttrib(int)}); this guard wires it into
 * the batch that {@code TileEntityRendererDispatcher.preDrawBatch} opens and {@code drawBatch}
 * draws ({@code MixinTileEntityRendererDispatcherBatch}). Celeritas renders the visible tile
 * entities, and those that mods add to {@code RenderGlobal.setTileEntities}, each inside one such
 * batch. Matrices are not covered here (push/pop attrib semantics): the dispatcher already wraps
 * each tile entity in its own matrix push/pop.
 *
 * <p>Actinium bracketed its own renderer's two tile-entity loops; upstream Celeritas's loops are
 * bracketed by the batch calls instead. Render thread only.
 */
public final class TileEntityGlStateGuard {
    /** Batches opened and not yet drawn; a batch nests only when a TESR renders another world view. */
    private static int openBatches;

    private TileEntityGlStateGuard() {
    }

    /**
     * A batch was opened: saves all GLSM-tracked GL state.
     */
    public static void onBatchOpened() {
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        openBatches++;
    }

    /**
     * The open batch is about to be drawn: restores the state saved when it opened and saves it
     * again. Modded TESRs leak GL state during the render loop, and the batch draw relies on
     * ambient state (texture units, alpha test, depth func/mask), so it must run with the clean
     * entry state. The re-save keeps {@link #onBatchDrawn()} balanced. Does nothing without an open
     * batch (a draw nobody opened).
     */
    public static void onBatchDrawing() {
        if (openBatches == 0) {
            return;
        }
        GLStateManager.glPopAttrib();
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    }

    /**
     * The batch was drawn: restores the state saved when it opened.
     */
    public static void onBatchDrawn() {
        if (openBatches == 0) {
            return;
        }
        openBatches--;
        GLStateManager.glPopAttrib();
    }
}
