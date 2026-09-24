package com.demonica.compat;

import com.demonica.compat.architecturecraft.ArchitectureCraftCompat;
import com.demonica.compat.componentmodelhider.ComponentModelHiderCompat;
import com.demonica.compat.snowrealmagic.SnowRealMagicCompat;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

/**
 * The blocks that Celeritas's fast block renderer leaves to vanilla's {@code BlockRendererDispatcher}, because a mod
 * renders or hides them from inside that dispatcher. S13's switch (docs/celeritas/patches/S13.md) asks this once per
 * block and layer; each check costs one constant read while its mod is absent.
 */
public final class FastBlockRendererCompat {
    private FastBlockRendererCompat() {
    }

    /** Whether the block at {@code pos} must be meshed by vanilla's dispatcher, whatever the fast renderer option says. */
    public static boolean requiresVanillaRenderer(Block block, BlockPos pos) {
        return SnowRealMagicCompat.shouldForceVanillaRender(block)
            || ArchitectureCraftCompat.shouldForceVanillaRender(block)
            || ComponentModelHiderCompat.hasHiddenNeighbour(pos);
    }
}
