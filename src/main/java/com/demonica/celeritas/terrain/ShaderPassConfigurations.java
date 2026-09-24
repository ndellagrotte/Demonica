package com.demonica.celeritas.terrain;

import com.demonica.celeritas.api.shader.PassSemantics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.MapMaker;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.CeleritasVintage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * S14 (docs/celeritas/patches/S14.md): the terrain passes Celeritas renders while a shader pack is active. Compared
 * with upstream's builder they add a separate water pass (fluids write depth so stacked water sorts; other
 * translucent terrain does not, so pass-1 block entities behind glass stay visible), put cutout geometry in the
 * cutout-mipped stage rather than the solid one when passes are consolidated, and tag every pass with its semantic
 * and depth policy ({@link PassSemantics}). Built from Actinium's pass builder.
 */
public final class ShaderPassConfigurations {
    private static final Logger LOGGER = LogManager.getLogger("Demonica");

    // Upstream's RenderPassConfiguration has no slot for a fluid material, so it is kept beside the configuration.
    // RenderPassConfiguration is a record, so the map compares keys by identity (Guava's weak keys) rather than
    // by content.
    private static final Map<RenderPassConfiguration<?>, Material> FLUID_MATERIALS = new MapMaker().weakKeys().makeMap();

    private ShaderPassConfigurations() {
    }

    /** The material fluids mesh into, or null for a configuration this class did not build. */
    public static @Nullable Material fluidMaterial(RenderPassConfiguration<?> configuration) {
        return FLUID_MATERIALS.get(configuration);
    }

    /** Whether a configuration was built here, i.e. for a shader pack. */
    public static boolean isShaderConfiguration(RenderPassConfiguration<?> configuration) {
        return FLUID_MATERIALS.containsKey(configuration);
    }

    private static TerrainRenderPass.PipelineState blocksTextureState(boolean mipped) {
        return new TerrainRenderPass.PipelineState() {
            @Override
            public void setup() {
                apply();
            }

            @Override
            public void clear() {
                apply();
            }

            private void apply() {
                // Reset the block atlas's mipmap state; mods sometimes leave it corrupted.
                Minecraft mc = Minecraft.getMinecraft();
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                ((AbstractTexture) mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)).setBlurMipmapDirect(false, mipped);
            }
        };
    }

    private static TerrainRenderPass.TerrainRenderPassBuilder pass(String name, ChunkVertexType vertexType, boolean mipped,
                                                                   PassSemantics.Semantic semantic, boolean writesDepth) {
        Map<String, String> defines = new HashMap<>(PassSemantics.defines(semantic, writesDepth));
        int fadeIn = CeleritasVintage.options().quality.chunkFadeInDuration;
        if (fadeIn > 0) {
            defines.put("CHUNK_FADE_IN_DURATION_MS", String.valueOf(fadeIn));
        }
        return TerrainRenderPass.builder()
            .name(name)
            .extraDefines(defines)
            .pipelineState(blocksTextureState(mipped))
            .vertexType(vertexType)
            .primitiveType(QuadPrimitiveType.TRIANGULATED);
    }

    public static RenderPassConfiguration<BlockRenderLayer> build(ChunkVertexType vertexType) {
        // Mipmapped sampling is only valid while mipmaps exist; the configuration is rebuilt on every reload.
        boolean mipped = Minecraft.getMinecraft().gameSettings.mipmapLevels > 0;
        boolean sortTranslucents = CeleritasVintage.options().performance.useTranslucentFaceSorting;

        TerrainRenderPass solidPass = pass("solid", vertexType, mipped, PassSemantics.Semantic.SOLID, true)
            .fragmentDiscard(false).useReverseOrder(false).build();
        TerrainRenderPass cutoutMippedPass = pass("cutout_mipped", vertexType, mipped, PassSemantics.Semantic.CUTOUT, true)
            .fragmentDiscard(true).useReverseOrder(false).build();
        TerrainRenderPass fluidPass = pass("water", vertexType, mipped, PassSemantics.Semantic.WATER, true)
            .fragmentDiscard(false).useReverseOrder(true).useTranslucencySorting(sortTranslucents).build();
        // Vanilla 1.12.2 draws the whole translucent stage with the depth mask off (EntityRenderer), so translucent
        // terrain must not occlude pass-1 block entities drawn after it (Actinium #58).
        TerrainRenderPass translucentPass = pass("translucent", vertexType, mipped, PassSemantics.Semantic.TRANSLUCENT, false)
            .fragmentDiscard(false).useReverseOrder(true).useTranslucencySorting(sortTranslucents).build();

        ImmutableListMultimap.Builder<BlockRenderLayer, TerrainRenderPass> stages = ImmutableListMultimap.builder();
        Material solidMaterial = new Material(solidPass, AlphaCutoffParameter.ZERO, mipped);
        Material translucentMaterial = new Material(translucentPass, AlphaCutoffParameter.ZERO, mipped);
        Material fluidMaterial = new Material(fluidPass, AlphaCutoffParameter.ZERO, mipped);
        Material cutoutMippedMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, mipped);
        Material cutoutMaterial;

        stages.put(BlockRenderLayer.SOLID, solidPass);
        stages.put(BlockRenderLayer.TRANSLUCENT, fluidPass);
        stages.put(BlockRenderLayer.TRANSLUCENT, translucentPass);

        if (CeleritasVintage.options().performance.useRenderPassConsolidation) {
            cutoutMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, mipped);
            stages.put(BlockRenderLayer.CUTOUT, cutoutMippedPass);
            stages.put(BlockRenderLayer.CUTOUT_MIPPED, cutoutMippedPass);
        } else {
            TerrainRenderPass cutoutPass = pass("cutout", vertexType, mipped, PassSemantics.Semantic.CUTOUT, true)
                .fragmentDiscard(true).useReverseOrder(false).build();
            cutoutMaterial = new Material(cutoutPass, AlphaCutoffParameter.ONE_TENTH, mipped);
            stages.put(BlockRenderLayer.CUTOUT, cutoutPass);
            stages.put(BlockRenderLayer.CUTOUT_MIPPED, cutoutMippedPass);
        }

        Map<BlockRenderLayer, Material> materials = new Reference2ReferenceOpenHashMap<>(4, Reference2ReferenceOpenHashMap.VERY_FAST_LOAD_FACTOR);
        materials.put(BlockRenderLayer.SOLID, solidMaterial);
        materials.put(BlockRenderLayer.CUTOUT, cutoutMaterial);
        materials.put(BlockRenderLayer.CUTOUT_MIPPED, cutoutMippedMaterial);
        materials.put(BlockRenderLayer.TRANSLUCENT, translucentMaterial);

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (!materials.containsKey(layer)) {
                LOGGER.warn("Falling back to cutout-like behavior for custom block render layer '{}'", layer);
                TerrainRenderPass extra = pass(layer.name().toLowerCase(Locale.ROOT), vertexType, mipped, PassSemantics.Semantic.CUTOUT, true)
                    .fragmentDiscard(true).useReverseOrder(false).build();
                stages.put(layer, extra);
                materials.put(layer, new Material(extra, AlphaCutoffParameter.ONE_TENTH, mipped));
            }
        }

        RenderPassConfiguration<BlockRenderLayer> configuration = new RenderPassConfiguration<>(materials, stages.build().asMap(),
            solidMaterial, cutoutMippedMaterial, translucentMaterial);
        FLUID_MATERIALS.put(configuration, fluidMaterial);
        return configuration;
    }
}
