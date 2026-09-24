package com.demonica.celeritas.terrain;

import com.demonica.celeritas.api.shader.vertex.BlockRenderContext;
import com.demonica.celeritas.api.shader.vertex.VanillaQuadContext;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.vertices.ExtendedDataHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What the meshing patches (S10, S11, S13) tell a shader pack about the blocks of a section built for its passes:
 * each block's ID from the pack's block.properties (with the snowy bit and block-entity NBT conditions), whether it
 * is a fluid, its light, and its position in the section. The vanilla path and the fast renderer resolve them the
 * same way, so a block looks the same on either. Called on the chunk builder threads.
 */
public final class ShaderBlockContexts {
    private ShaderBlockContexts() {
    }

    /** The context of the quads {@code state} renders at {@code pos}. */
    public static VanillaQuadContext of(IBlockState state, BlockPos pos, IBlockAccess world) {
        Block block = state.getBlock();
        boolean fluid = isFluid(state);
        int metadata = block.getMetaFromState(state);
        if (isSnowy(block, pos, world)) {
            metadata |= BlockMaterialMapping.SNOWY_META_BIT;
        }
        int blockId = BlockRenderingSettings.INSTANCE.getBlockStateId(block, metadata);
        if (blockId == -1 && fluid) {
            blockId = liquidCounterpartId(block, metadata);
        }
        int nbtBlockId = nbtBlockId(state, pos, world);
        if (nbtBlockId != -1) {
            blockId = nbtBlockId;
        }
        short renderType = fluid ? ExtendedDataHelper.FLUID_RENDER_TYPE : ExtendedDataHelper.BLOCK_RENDER_TYPE;
        return new VanillaQuadContext(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, blockId, renderType,
            (byte) state.getLightValue(world, pos));
    }

    /** Copies a quad's context into the one the extended vertex encoder reads. */
    public static BlockRenderContext apply(VanillaQuadContext context, BlockRenderContext target) {
        target.set(context.localPosX(), context.localPosY(), context.localPosZ(), context.blockStateId(), context.renderType(),
            context.lightValue());
        return target;
    }

    /** Water and lava, including modded fluid blocks of those materials. */
    public static boolean isFluid(IBlockState state) {
        Material material = state.getMaterial();
        return material == Material.WATER || material == Material.LAVA;
    }

    /** Whether a quad with this context is a fluid's. */
    public static boolean isFluid(@Nullable VanillaQuadContext context) {
        return context != null && context.renderType() == ExtendedDataHelper.FLUID_RENDER_TYPE;
    }

    /**
     * The material a fluid's quads mesh into: its translucent quads go to the pack's water pass (S14), which writes
     * depth, and all of them skip Celeritas's sprite-based downgrade of the material.
     */
    public static org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material fluidMaterial(
        RenderPassConfiguration<?> configuration, org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material layerMaterial) {
        var water = ShaderPassConfigurations.fluidMaterial(configuration);
        return water != null && layerMaterial == configuration.defaultTranslucentMaterial() ? water : layerMaterial;
    }

    /** The pack's layer for a block it moves (block.properties' {@code layer.*}), or null. */
    public static @Nullable BlockRenderLayer layerOverride(Block block) {
        Map<Block, com.demonica.celeritas.api.shader.BlockRenderLayer> overrides = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
        if (overrides == null) {
            return null;
        }
        var layer = overrides.get(block);
        return layer != null ? layer.toVanillaLayer() : null;
    }

    private static boolean isSnowy(Block block, BlockPos pos, IBlockAccess world) {
        if (!BlockRenderingSettings.INSTANCE.hasSnowyEntries() || !BlockRenderingSettings.INSTANCE.getSnowyBlocks().contains(block)) {
            return false;
        }
        Block above = world.getBlockState(pos.up()).getBlock();
        return above == Blocks.SNOW_LAYER || above == Blocks.SNOW;
    }

    // 1.12 splits water and lava into still and flowing blocks, where a pack written for newer versions names only
    // "water" and "lava".
    private static int liquidCounterpartId(Block block, int metadata) {
        Block counterpart = block == Blocks.FLOWING_WATER ? Blocks.WATER
            : block == Blocks.WATER ? Blocks.FLOWING_WATER
            : block == Blocks.FLOWING_LAVA ? Blocks.LAVA
            : block == Blocks.LAVA ? Blocks.FLOWING_LAVA
            : null;
        return counterpart != null ? BlockRenderingSettings.INSTANCE.getBlockStateId(counterpart, metadata) : -1;
    }

    private static int nbtBlockId(IBlockState state, BlockPos pos, IBlockAccess world) {
        if (BlockRenderingSettings.INSTANCE.getBlockNbtMap() == null || !state.getBlock().hasTileEntity(state)) {
            return -1;
        }
        TileEntity tileEntity = world.getTileEntity(pos);
        return BlockRenderingSettings.INSTANCE.resolveBlockNbtId(state.getBlock(), tileEntity);
    }
}
