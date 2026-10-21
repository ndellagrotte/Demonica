package com.demonica.compat.movingelevators;

import com.supermartijn642.movingelevators.elevator.ElevatorGroupRenderer;

/**
 * Compatibility for {@code Moving Elevators} (mod id {@code movingelevators}).
 *
 * <p>Moving Elevators draws the block entities of a moving cage itself, in {@code ElevatorGroupRenderer
 * .renderBlockEntities}, which it calls from two mixins of its own: right after vanilla's
 * {@code TileEntityRendererDispatcher.preDrawBatch()} in {@code RenderGlobal.renderEntities}, and at the same point
 * in Vintagium's {@code SodiumWorldRenderer.renderTileEntities}. Celeritas cancels {@code renderEntities} before that
 * call and has no {@code SodiumWorldRenderer}, so neither runs and a chest or sign in a moving cage disappears for
 * the ride. C3 (docs/celeritas/patches/C3.md) calls {@link #renderCageBlockEntities} at the same point of Celeritas's
 * block-entity pass instead (docs/compat/moving-elevators.md).
 *
 * <p>Called only behind {@code Mods.MOVINGELEVATORS}, so the mod's classes load only when it is present.
 */
public final class MovingElevatorsCompat {
    private MovingElevatorsCompat() {
    }

    /** Draws the block entities of every moving cage in range, inside the open tile-entity batch. */
    public static void renderCageBlockEntities(float partialTicks) {
        ElevatorGroupRenderer.renderBlockEntities(partialTicks);
    }
}
