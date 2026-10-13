package com.demonica.render.vertex;

import com.gtnewhorizons.angelica.glsm.hooks.QuadSideAttributeWriter;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.vertices.NormI8;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuadSideAttributes}: the per-quad mc_midTexCoord and at_tangent of vanilla GL_QUADS geometry, in the
 * encoding and at the attribute locations Iris's entity vertex format uses.
 */
class QuadSideAttributesTest {

    /** OLDMODEL_POSITION_TEX_NORMAL: position at 0, texture coordinate at 12, normal at 20, 24 bytes. */
    private static final int STRIDE = 24;

    @Test
    void locationsAndEncodingMatchIris() {
        assertEquals(ProgramCreator.MC_MID_TEX_COORD, QuadSideAttributeWriter.MID_TEX_COORD_LOCATION);
        assertEquals(ProgramCreator.AT_TANGENT, QuadSideAttributeWriter.TANGENT_LOCATION);
        assertEquals(NormI8.pack(1.0f, 0.0f, 0.0f, 1.0f), QuadSideAttributeWriter.NEUTRAL_TANGENT);
    }

    @Test
    void layoutFindsPositionAndTextureCoordinateOfModelAndItemFormats() {
        assertArrayEquals(new int[] {0, 12}, QuadSideAttributes.layout(DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL));
        assertArrayEquals(new int[] {0, 16}, QuadSideAttributes.layout(DefaultVertexFormats.ITEM));
        assertArrayEquals(new int[] {0, 12}, QuadSideAttributes.layout(DefaultVertexFormats.POSITION_TEX_NORMAL));
    }

    @Test
    void layoutRejectsFormatsWithoutNormalOrTextureCoordinate() {
        assertNull(QuadSideAttributes.layout(DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP));
        assertNull(QuadSideAttributes.layout(DefaultVertexFormats.POSITION_TEX));
        assertNull(QuadSideAttributes.layout(DefaultVertexFormats.POSITION_COLOR));
        assertNull(QuadSideAttributes.layout(DefaultVertexFormats.POSITION));
    }

    @Test
    void quadGetsItsMidTextureCoordinateAndATangentAlongU() {
        // A unit quad on the XZ plane facing +Y, texture u running along +X and v along +Z.
        ByteBuffer vertices = vertices(
            0, 0, 0, 0.25f, 0.5f,
            0, 0, 1, 0.25f, 0.75f,
            1, 0, 1, 0.5f, 0.75f,
            1, 0, 0, 0.5f, 0.5f);
        ByteBuffer side = side(4);

        QuadSideAttributes.INSTANCE.write(MemoryUtil.memAddress(vertices), STRIDE, 0, 12, 4, MemoryUtil.memAddress(side));

        for (int v = 0; v < 4; v++) {
            int base = v * QuadSideAttributeWriter.STRIDE;
            assertEquals(0.375f, side.getFloat(base), 1e-6f, "midU of vertex " + v);
            assertEquals(0.625f, side.getFloat(base + 4), 1e-6f, "midV of vertex " + v);
            float[] tangent = unpack(side.getInt(base + QuadSideAttributeWriter.TANGENT_OFFSET));
            assertEquals(1.0f, tangent[0], 0.02f, "tangent x of vertex " + v);
            assertEquals(0.0f, tangent[1], 0.02f, "tangent y of vertex " + v);
            assertEquals(0.0f, tangent[2], 0.02f, "tangent z of vertex " + v);
            assertEquals(1.0f, Math.abs(tangent[3]), 0.02f, "handedness of vertex " + v);
        }
    }

    @Test
    void verticalQuadTangentFollowsItsTextureAndStaysPerpendicularToTheNormal() {
        // A quad on the XY plane facing -Z (the front of a model box), u along +X and v downwards along -Y.
        ByteBuffer vertices = vertices(
            0, 1, 0, 0.0f, 0.0f,
            0, 0, 0, 0.0f, 1.0f,
            1, 0, 0, 1.0f, 1.0f,
            1, 1, 0, 1.0f, 0.0f);
        ByteBuffer side = side(4);

        QuadSideAttributes.INSTANCE.write(MemoryUtil.memAddress(vertices), STRIDE, 0, 12, 4, MemoryUtil.memAddress(side));

        float[] tangent = unpack(side.getInt(QuadSideAttributeWriter.TANGENT_OFFSET));
        assertEquals(1.0f, tangent[0], 0.02f);
        assertEquals(0.0f, tangent[1], 0.02f);
        assertEquals(0.0f, tangent[2], 0.02f);
        assertEquals(1.0f, Math.abs(tangent[3]), 0.02f);
    }

    @Test
    void degenerateTextureCoordinatesGetTheNeutralTangent() {
        ByteBuffer vertices = vertices(
            0, 0, 0, 0.5f, 0.5f,
            0, 0, 1, 0.5f, 0.5f,
            1, 0, 1, 0.5f, 0.5f,
            1, 0, 0, 0.5f, 0.5f);
        ByteBuffer side = side(4);

        QuadSideAttributes.INSTANCE.write(MemoryUtil.memAddress(vertices), STRIDE, 0, 12, 4, MemoryUtil.memAddress(side));

        assertEquals(QuadSideAttributeWriter.NEUTRAL_TANGENT, side.getInt(QuadSideAttributeWriter.TANGENT_OFFSET));
        assertEquals(0.5f, side.getFloat(0), 1e-6f);
    }

    @Test
    void trailingPartialQuadGetsTheNeutralValues() {
        ByteBuffer vertices = vertices(
            0, 0, 0, 0, 0,
            0, 0, 1, 0, 1,
            1, 0, 1, 1, 1,
            1, 0, 0, 1, 0,
            0, 0, 0, 0, 0,
            0, 0, 1, 0, 1);
        ByteBuffer side = side(6);

        QuadSideAttributes.INSTANCE.write(MemoryUtil.memAddress(vertices), STRIDE, 0, 12, 6, MemoryUtil.memAddress(side));

        for (int v = 4; v < 6; v++) {
            int base = v * QuadSideAttributeWriter.STRIDE;
            assertEquals(0.5f, side.getFloat(base), 1e-6f);
            assertEquals(0.5f, side.getFloat(base + 4), 1e-6f);
            assertEquals(QuadSideAttributeWriter.NEUTRAL_TANGENT, side.getInt(base + QuadSideAttributeWriter.TANGENT_OFFSET));
        }
        assertTrue(side.getInt(QuadSideAttributeWriter.TANGENT_OFFSET) != 0, "the whole quad keeps a computed tangent");
    }

    @Test
    void writeNeutralFillsEveryVertex() {
        ByteBuffer side = side(3);
        QuadSideAttributeWriter.writeNeutral(MemoryUtil.memAddress(side), 3);
        for (int v = 0; v < 3; v++) {
            int base = v * QuadSideAttributeWriter.STRIDE;
            assertEquals(0.5f, side.getFloat(base), 1e-6f);
            assertEquals(0.5f, side.getFloat(base + 4), 1e-6f);
            assertEquals(QuadSideAttributeWriter.NEUTRAL_TANGENT, side.getInt(base + QuadSideAttributeWriter.TANGENT_OFFSET));
        }
    }

    /** Vertices as x, y, z, u, v groups in the OLDMODEL_POSITION_TEX_NORMAL layout (the normal bytes stay zero). */
    private static ByteBuffer vertices(float... values) {
        int count = values.length / 5;
        ByteBuffer buffer = ByteBuffer.allocateDirect(count * STRIDE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            int base = i * STRIDE;
            buffer.putFloat(base, values[i * 5]);
            buffer.putFloat(base + 4, values[i * 5 + 1]);
            buffer.putFloat(base + 8, values[i * 5 + 2]);
            buffer.putFloat(base + 12, values[i * 5 + 3]);
            buffer.putFloat(base + 16, values[i * 5 + 4]);
        }
        return buffer;
    }

    private static ByteBuffer side(int vertexCount) {
        return ByteBuffer.allocateDirect(vertexCount * QuadSideAttributeWriter.STRIDE).order(ByteOrder.nativeOrder());
    }

    /** The four normalized signed bytes of a packed tangent as x, y, z, w. */
    private static float[] unpack(int packed) {
        return new float[] {
            (byte) packed / 127.0f,
            (byte) (packed >> 8) / 127.0f,
            (byte) (packed >> 16) / 127.0f,
            (byte) (packed >> 24) / 127.0f,
        };
    }
}
