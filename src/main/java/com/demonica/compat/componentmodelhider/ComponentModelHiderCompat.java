package com.demonica.compat.componentmodelhider;

import com.demonica.compat.Mods;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * Compatibility for the Component Model Hider (mod id {@code component_model_hider}), the standalone
 * extraction of Multiblocked's block-model hiding API that Modular Machinery CE drives through
 * {@code com.cleanroommc.multiblocked.persistence.MultiblockWorldSavedData}.
 *
 * <p>The hider performs its hiding from inside the vanilla chunk rebuild: its {@code RenderChunkMixin}
 * redirects the {@code BlockRendererDispatcher#renderBlock} call in
 * {@code RenderChunk#rebuildChunk}, returns {@code false} for a disabled position so the block emits
 * no geometry, and arms {@code MultiblockWorldSavedData.isBuildingChunk} around that call. The armed
 * flag is what makes the rest of the hider work: {@code MultiblockWorldSavedData#isModelDisabled}
 * reports a position as disabled only while it is set, which is the signal its
 * {@code BlockModelRenderer} / {@code ForgeBlockModelRenderer} redirects and its CodeChickenLib hooks
 * consume, so a hidden block stops culling the faces of its neighbours while everything outside a
 * chunk build (the block breaking and damage overlays) keeps drawing normally. Its
 * {@code TileEntityRendererDispatcher#getRenderer} injection is not gated at all and consults the
 * disabled-position set directly.</p>
 *
 * <p>Its {@code BlockVisitor} transformer is not part of that in 1.12.2: it looks for a
 * {@code Block.doesSideBlockRendering} method to hook, and 1.12.2 has no such method - its face
 * culling lives in {@code Block#shouldSideBeRendered}, which ends in
 * {@code !blockAccess.getBlockState(pos.offset(side)).isOpaqueCube()}. The neighbour rule is therefore
 * carried by the {@code BlockModelRenderer} redirects alone.</p>
 *
 * <p>Celeritas meshes terrain in {@code ChunkBuilderMeshingTask} and never calls
 * {@code RenderChunk#rebuildChunk}, so neither half of that contract runs by itself: hidden blocks are
 * still meshed, and Celeritas's fast block renderer calls {@code shouldSideBeRendered} itself instead of
 * going through {@code BlockModelRenderer}, so their neighbours are still culled. Demonica re-applies
 * both halves from the quarantine (patch C1, docs/celeritas/LEDGER.md): the build gate that hands the
 * {@code BlockModelRenderer} and CodeChickenLib decisions back to the hider's own hooks, the
 * per-position skip, and, for the neighbour rule, the vanilla dispatcher path for every block next to a
 * hidden one (S13's switch), where the hider's redirects apply it. Nothing else about how a block is
 * meshed changes.</p>
 *
 * <p>No hider class is ever touched while the mod is absent: {@link #IS_LOADED} is read from
 * {@link Mods}, and every foreign reference lives in {@link ComponentModelHiderBridge}, which is only
 * loaded once that flag is true. Hot-path call sites test {@link #IS_LOADED} first, so a client
 * without the mod neither loads the bridge nor allocates neighbour positions.</p>
 */
public final class ComponentModelHiderCompat {
    /**
     * The mod id the hider ships under (CurseForge project 940949, file 4885858).
     */
    public static final String MODID = "component_model_hider";

    /**
     * Whether the hider is present. Read once here so that a call site can decide with a constant
     * instead of probing the mod list per block.
     */
    public static final boolean IS_LOADED = Mods.COMPONENT_MODEL_HIDER;

    private ComponentModelHiderCompat() {
    }

    /**
     * Returns whether the block model at {@code pos} is currently hidden. Only answers while
     * {@link #beginBuild()} is in effect on the calling thread, which is the same condition the
     * hider's own {@code isModelDisabled} applies, so in-world overlays outside a chunk build keep
     * rendering hidden positions.
     */
    public static boolean isHidden(BlockPos pos) {
        if (!IS_LOADED) {
            return false;
        }
        return ComponentModelHiderBridge.isHidden(pos);
    }

    /**
     * Returns whether any neighbour of {@code pos} is hidden. Such a block must take the vanilla dispatcher
     * path: the hider teaches vanilla that a hidden block does not occlude through its redirects inside
     * {@code BlockModelRenderer}, which Celeritas's fast block renderer does not run, so on that path a hidden
     * block would still cull the faces looking at it and punch a see-through hole into its neighbours.
     *
     * <p>The offsets are taken only when the hider is present, so a client without the mod pays a single
     * constant read per block.</p>
     */
    public static boolean hasHiddenNeighbour(BlockPos pos) {
        if (!IS_LOADED) {
            return false;
        }
        for (EnumFacing dir : EnumFacing.VALUES) {
            if (ComponentModelHiderBridge.isHidden(pos.offset(dir))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Arms the hider's culling hooks for one chunk mesh build on the calling thread. Mirrors what
     * {@code RenderChunkMixin} does around each vanilla {@code renderBlock} call, but for the whole
     * build: inside the build the flag only affects the culling queries the hider hooks, and no
     * other thread is affected because it is thread-local.
     */
    public static void beginBuild() {
        if (IS_LOADED) {
            ComponentModelHiderBridge.beginBuild();
        }
    }

    /**
     * Disarms the hooks armed by {@link #beginBuild()}, restoring the state a fresh worker thread
     * starts from.
     */
    public static void endBuild() {
        if (IS_LOADED) {
            ComponentModelHiderBridge.endBuild();
        }
    }
}
