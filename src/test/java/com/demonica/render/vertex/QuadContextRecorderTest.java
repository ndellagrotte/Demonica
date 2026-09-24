package com.demonica.render.vertex;

import com.demonica.celeritas.api.shader.vertex.VanillaQuadContext;
import net.coderbot.iris.celeritas.buffer.ShaderMaterialOverrideState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Entry n of the recorded contexts always belongs to quad n, whatever else draws into the buffer. */
class QuadContextRecorderTest {
    private static final VanillaQuadContext GRASS = new VanillaQuadContext(1, 2, 3, 10005, (short) 0, (byte) 0);
    private static final VanillaQuadContext WATER = new VanillaQuadContext(4, 5, 6, 32000, (short) 1, (byte) 0);

    @Test
    void recordsNothingUntilAContextIsAttached() {
        QuadContextRecorder recorder = new QuadContextRecorder();
        recorder.onVerticesAdded(3);

        assertTrue(recorder.consume(3).isEmpty());
    }

    @Test
    void quadsDrawnWithoutAContextKeepTheirPlace() {
        QuadContextRecorder recorder = new QuadContextRecorder();
        // Two quads before the first block with a context.
        recorder.setActive(GRASS, 2);
        recorder.onVerticesAdded(3);
        recorder.setActive(null, 3);
        // Two quads drawn between blocks, such as Fluidlogged's fluids.
        recorder.onVerticesAdded(5);
        recorder.setActive(WATER, 5);
        recorder.onVerticesAdded(6);
        recorder.setActive(null, 6);

        // A last quad appended without going through the vertex hooks (putBulkData).
        List<VanillaQuadContext> contexts = recorder.consume(7);

        assertEquals(Arrays.asList(null, null, GRASS, null, null, WATER, null), contexts);
        assertSame(GRASS, contexts.get(2));
        assertSame(WATER, contexts.get(5));
    }

    @Test
    void severalQuadsAddedAtOnceShareTheActiveContext() {
        QuadContextRecorder recorder = new QuadContextRecorder();
        recorder.setActive(GRASS, 0);
        recorder.onVerticesAdded(2);

        assertEquals(Arrays.asList(GRASS, GRASS), recorder.consume(2));
    }

    @Test
    void aShaderMaterialOverrideReplacesTheBlockId() {
        QuadContextRecorder recorder = new QuadContextRecorder();
        recorder.setActive(GRASS, 0);
        ShaderMaterialOverrideState.setBlockId(42);
        try {
            recorder.onVerticesAdded(1);
        } finally {
            ShaderMaterialOverrideState.clear();
        }
        recorder.onVerticesAdded(2);

        List<VanillaQuadContext> contexts = recorder.consume(2);
        assertEquals(42, contexts.get(0).blockStateId());
        assertSame(GRASS, contexts.get(1));
    }

    @Test
    void consumingForgetsTheContexts() {
        QuadContextRecorder recorder = new QuadContextRecorder();
        recorder.setActive(GRASS, 0);
        recorder.onVerticesAdded(1);
        recorder.consume(1);

        recorder.onVerticesAdded(2);
        assertTrue(recorder.consume(2).isEmpty());
    }
}
