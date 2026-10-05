package net.coderbot.iris.pipeline;

import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The gbuffer program table ({@link DeferredWorldRenderingPipeline#GBUFFER_PROGRAM_IDS}) is indexed by
 * {@link RenderCondition} ordinal, three entries per condition. Plan item 2.3 added the {@code PARTICLES} row; these
 * checks keep rows and conditions aligned as more are added.
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

        assertEquals(RenderCondition.SHADOW, conditions[conditions.length - 1]);
        assertEquals(RenderCondition.SHADOW_TRANSLUCENT, conditions[conditions.length - 2]);
    }

    @Test
    void onlyShadowConditionsUseShadowPrograms() {
        for (RenderCondition condition : RenderCondition.values()) {
            boolean shadowCondition = condition == RenderCondition.SHADOW || condition == RenderCondition.SHADOW_TRANSLUCENT;

            for (ProgramId id : row(condition)) {
                if (id != null) {
                    assertEquals(shadowCondition, id.getGroup() == ProgramGroup.Shadow, condition + " -> " + id);
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
    void particlesTransHasNoRow() {
        assertFalse(Arrays.asList(DeferredWorldRenderingPipeline.GBUFFER_PROGRAM_IDS).contains(ProgramId.ParticlesTrans));
    }
}
