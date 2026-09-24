package com.demonica.celeritas.terrain;

import com.demonica.celeritas.api.shader.PassSemantics;
import net.minecraft.util.BlockRenderLayer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S14's pass configuration (ShaderPassConfigurations), with Celeritas's pass consolidation on (its default) and off.
 * Celeritas keys render lists and programs by pass, and draws every pass of a layer's stage each time vanilla draws
 * that layer, so the water pass must stay a pass of its own and no pass may be staged under two layers.
 */
class PassConfigurationTest {
    private static final ChunkVertexType VERTEX_TYPE = ChunkMeshFormats.VANILLA_LIKE;

    private static RenderPassConfiguration<BlockRenderLayer> build(boolean consolidatePasses) {
        return ShaderPassConfigurations.build(VERTEX_TYPE, true, true, consolidatePasses, 0);
    }

    @ParameterizedTest(name = "pass consolidation {0}")
    @ValueSource(booleans = {true, false})
    void waterIsNotTranslucent(boolean consolidatePasses) {
        RenderPassConfiguration<BlockRenderLayer> configuration = build(consolidatePasses);
        TerrainRenderPass water = ShaderPassConfigurations.fluidMaterial(configuration).pass;
        TerrainRenderPass translucent = configuration.defaultTranslucentMaterial().pass;

        assertNotEquals(translucent, water, "a pass equal to the translucent one would share its render lists and program");
        assertEquals(PassSemantics.Semantic.WATER, PassSemantics.semantic(water));
        assertEquals(PassSemantics.Semantic.TRANSLUCENT, PassSemantics.semantic(translucent));
        // Stacked water sorts by depth; other translucent terrain leaves depth to the block entities drawn after it.
        assertTrue(PassSemantics.writesDepth(water));
        assertFalse(PassSemantics.writesDepth(translucent));
        assertEquals(List.of(water, translucent), List.copyOf(configuration.vanillaRenderStages().get(BlockRenderLayer.TRANSLUCENT)));
    }

    @ParameterizedTest(name = "pass consolidation {0}")
    @ValueSource(booleans = {true, false})
    void noPassIsStagedTwice(boolean consolidatePasses) {
        Map<BlockRenderLayer, Collection<TerrainRenderPass>> stages = build(consolidatePasses).vanillaRenderStages();
        Set<TerrainRenderPass> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<TerrainRenderPass> equal = new HashSet<>();
        List<String> repeated = new ArrayList<>();
        stages.forEach((layer, passes) -> {
            for (TerrainRenderPass pass : passes) {
                if (!seen.add(pass) || !equal.add(pass)) {
                    repeated.add(pass.name() + " in " + layer);
                }
            }
        });
        assertTrue(repeated.isEmpty(), "passes staged twice would be drawn twice a frame: " + repeated);
    }

    @ParameterizedTest(name = "pass consolidation {0}")
    @ValueSource(booleans = {true, false})
    void everyPassIsTaggedForThePack(boolean consolidatePasses) {
        build(consolidatePasses).vanillaRenderStages().values().forEach(passes -> passes.forEach(pass ->
            assertTrue(PassSemantics.isTagged(pass), pass.name() + " carries no pass semantic for the pack's programs")));
    }

    /** Consolidated, both cutout layers mesh into the cutout-mipped pass, which is drawn with the cutout-mipped layer. */
    @ParameterizedTest(name = "pass consolidation {0}")
    @ValueSource(booleans = {true, false})
    void cutoutGeometryIsDrawnOnce(boolean consolidatePasses) {
        RenderPassConfiguration<BlockRenderLayer> configuration = build(consolidatePasses);
        TerrainRenderPass cutout = configuration.getMaterialForRenderType(BlockRenderLayer.CUTOUT).pass;
        TerrainRenderPass cutoutMipped = configuration.getMaterialForRenderType(BlockRenderLayer.CUTOUT_MIPPED).pass;
        Collection<TerrainRenderPass> cutoutStage = configuration.vanillaRenderStages().getOrDefault(BlockRenderLayer.CUTOUT, List.of());
        Collection<TerrainRenderPass> mippedStage = configuration.vanillaRenderStages().get(BlockRenderLayer.CUTOUT_MIPPED);
        if (consolidatePasses) {
            assertSame(cutoutMipped, cutout);
            assertTrue(cutoutStage.isEmpty(), "the cutout stage would draw the consolidated pass a second time");
        } else {
            assertEquals(List.of(cutout), List.copyOf(cutoutStage));
        }
        assertEquals(List.of(cutoutMipped), List.copyOf(mippedStage));
        assertEquals(PassSemantics.Semantic.CUTOUT, PassSemantics.semantic(cutout));
    }
}
