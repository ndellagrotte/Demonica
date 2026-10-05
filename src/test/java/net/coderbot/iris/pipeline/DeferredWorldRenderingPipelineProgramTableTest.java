package net.coderbot.iris.pipeline;

import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The gbuffer program table ({@link DeferredWorldRenderingPipeline#GBUFFER_PROGRAM_IDS}) is indexed by
 * {@link RenderCondition} ordinal, three entries per condition. Plan item 2.3 added the {@code PARTICLES} row and 2.4
 * the {@code LIGHTNING} and three shadow rows; these checks keep rows and conditions aligned as more are added. 2.4's
 * shadow-pass selection ({@link DeferredWorldRenderingPipeline#getShadowCondition}) is checked here too.
 */
class DeferredWorldRenderingPipelineProgramTableTest {
    private static ProgramId[] row(RenderCondition condition) {
        int start = condition.ordinal() * 3;
        return Arrays.copyOfRange(DeferredWorldRenderingPipeline.GBUFFER_PROGRAM_IDS, start, start + 3);
    }

    @Test
    void tableHasThreeEntriesPerCondition() {
        assertEquals(RenderCondition.values().length * 3, DeferredWorldRenderingPipeline.GBUFFER_PROGRAM_IDS.length);
    }

    @Test
    void shadowConditionsStayLast() {
        RenderCondition[] conditions = RenderCondition.values();
        boolean seenShadow = false;

        for (RenderCondition condition : conditions) {
            if (condition.isShadow()) {
                seenShadow = true;
            } else {
                assertFalse(seenShadow, condition + " comes after a shadow condition");
            }
        }
        assertEquals(RenderCondition.SHADOW, conditions[conditions.length - 1]);
        assertEquals(RenderCondition.SHADOW_TRANSLUCENT, conditions[conditions.length - 2]);
    }

    @Test
    void isShadowNamesTheFiveShadowConditions() {
        EnumSet<RenderCondition> shadow = EnumSet.noneOf(RenderCondition.class);
        for (RenderCondition condition : RenderCondition.values()) {
            if (condition.isShadow()) {
                shadow.add(condition);
            }
        }

        assertEquals(EnumSet.of(RenderCondition.SHADOW_ENTITIES, RenderCondition.SHADOW_LIGHTNING,
            RenderCondition.SHADOW_BLOCK, RenderCondition.SHADOW_TRANSLUCENT, RenderCondition.SHADOW), shadow);
    }

    @Test
    void onlyShadowConditionsUseShadowPrograms() {
        for (RenderCondition condition : RenderCondition.values()) {
            for (ProgramId id : row(condition)) {
                if (id != null) {
                    assertEquals(condition.isShadow(), id.getGroup() == ProgramGroup.Shadow, condition + " -> " + id);
                }
            }
        }
    }

    @Test
    void particlesUseParticlesProgram() {
        assertArrayEquals(new ProgramId[] {null, ProgramId.Particles, ProgramId.Particles}, row(RenderCondition.PARTICLES));
        assertArrayEquals(new ProgramId[] {ProgramId.ShadowWater, ProgramId.ShadowWater, ProgramId.ShadowWater},
            row(RenderCondition.SHADOW_TRANSLUCENT));
        assertArrayEquals(new ProgramId[] {ProgramId.Shadow, ProgramId.Shadow, ProgramId.Shadow},
            row(RenderCondition.SHADOW));
    }

    @Test
    void lightningAndShadowRowsUseTheirPrograms() {
        assertArrayEquals(new ProgramId[] {ProgramId.Lightning, ProgramId.Lightning, ProgramId.Lightning},
            row(RenderCondition.LIGHTNING));
        assertArrayEquals(new ProgramId[] {ProgramId.ShadowEntities, ProgramId.ShadowEntities, ProgramId.ShadowEntities},
            row(RenderCondition.SHADOW_ENTITIES));
        assertArrayEquals(new ProgramId[] {ProgramId.ShadowLightning, ProgramId.ShadowLightning, ProgramId.ShadowLightning},
            row(RenderCondition.SHADOW_LIGHTNING));
        assertArrayEquals(new ProgramId[] {ProgramId.ShadowBlock, ProgramId.ShadowBlock, ProgramId.ShadowBlock},
            row(RenderCondition.SHADOW_BLOCK));
    }

    @Test
    void shadowPassPicksByPhaseAndSpecialCondition() {
        assertEquals(RenderCondition.SHADOW, DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.NONE, null));
        assertEquals(RenderCondition.SHADOW,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.TERRAIN_SOLID, null));
        assertEquals(RenderCondition.SHADOW_TRANSLUCENT,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.TERRAIN_TRANSLUCENT, null));
        assertEquals(RenderCondition.SHADOW_ENTITIES,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.ENTITIES, null));
        // Upstream gives block-entity models shadow_entities; only the end portal and gateway take shadow_block
        assertEquals(RenderCondition.SHADOW_ENTITIES,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.BLOCK_ENTITIES, null));
        assertEquals(RenderCondition.SHADOW_ENTITIES,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.BLOCK_ENTITIES, SpecialCondition.BEACON_BEAM));
        assertEquals(RenderCondition.SHADOW_ENTITIES,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.ENTITIES, SpecialCondition.GLINT));
        assertEquals(RenderCondition.SHADOW_BLOCK,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.BLOCK_ENTITIES, SpecialCondition.END_PORTAL));
        assertEquals(RenderCondition.SHADOW_LIGHTNING,
            DeferredWorldRenderingPipeline.getShadowCondition(WorldRenderingPhase.ENTITIES, SpecialCondition.LIGHTNING));
    }

    @Test
    void lightningProgramsTurnAlphaTestOff() {
        assertSame(AlphaTestOverride.OFF, ProgramId.Lightning.getDefaultAlphaTestOverride());
        assertSame(AlphaTestOverride.OFF, ProgramId.ShadowLightning.getDefaultAlphaTestOverride());
    }

    @Test
    void particlesTransHasNoRow() {
        assertFalse(Arrays.asList(DeferredWorldRenderingPipeline.GBUFFER_PROGRAM_IDS).contains(ProgramId.ParticlesTrans));
    }
}
