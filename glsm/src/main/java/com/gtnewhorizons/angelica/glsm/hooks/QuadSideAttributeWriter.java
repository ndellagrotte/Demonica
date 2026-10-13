package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

/**
 * Computes the per-quad shader attributes {@code mc_midTexCoord} and {@code at_tangent} for {@code GL_QUADS} geometry
 * drawn through the vanilla paths (entity models, items, item frames, the hand): Iris's entity vertex format gives
 * every vertex of a quad the quad's mid-texture coordinate and tangent, and shader packs with normal mapping or
 * parallax ({@code ADVANCED_MATERIALS} in BSL and its derivatives) read them on every gbuffers program. Without them a
 * pack sees one constant per entity (GbufferPrograms.setBlockEntityDefaults), which breaks parallax on every face and
 * makes the tangent frame degenerate on faces parallel to the constant tangent.
 *
 * <p>The side buffer holds {@value #STRIDE} bytes per vertex: the mid-texture coordinate as two floats, then the
 * tangent as four normalized signed bytes (xyz and the handedness in w), the encoding of Iris's entity format. The
 * attribute locations are the ones every Iris program binds ({@code ProgramCreator.MC_MID_TEX_COORD} and
 * {@code AT_TANGENT}; checked against this interface by QuadSideAttributesTest).</p>
 *
 * <p>The implementation lives in the root project (it uses Iris's tangent math), installed as
 * {@link GLSMHooks#quadSideAttributes}; the display-list compiler reads the hook, the streaming draw path calls the
 * implementation directly.</p>
 */
public interface QuadSideAttributeWriter {
    /** Bytes per vertex in the side buffer. */
    int STRIDE = 12;
    /** Offset of the tangent inside a side vertex; the mid-texture coordinate is at 0. */
    int TANGENT_OFFSET = 8;
    /** Generic attribute location of {@code mc_midTexCoord}. */
    int MID_TEX_COORD_LOCATION = 12;
    /** Generic attribute location of {@code at_tangent}. */
    int TANGENT_LOCATION = 13;
    /**
     * The tangent {@code (1, 0, 0, 1)} packed as four signed bytes: what GbufferPrograms.setBlockEntityDefaults gives
     * geometry that has no computed tangent.
     */
    int NEUTRAL_TANGENT = 0x7F00007F;

    /**
     * Writes the side attributes of {@code vertexCount} vertices of {@code GL_QUADS} geometry.
     *
     * @param source          address of the first vertex
     * @param stride          bytes per source vertex
     * @param positionOffset  offset of the three float position components inside a source vertex
     * @param texCoordOffset  offset of the two float primary texture coordinates inside a source vertex
     * @param vertexCount     vertices to process; a trailing partial quad gets the neutral values
     * @param destination     address of the side buffer, {@code vertexCount * STRIDE} bytes
     */
    void write(long source, int stride, int positionOffset, int texCoordOffset, int vertexCount, long destination);

    /** Writes the neutral attributes (mid-texture coordinate 0.5, 0.5 and the neutral tangent) for {@code vertexCount} vertices. */
    static void writeNeutral(long destination, int vertexCount) {
        for (int i = 0; i < vertexCount; i++) {
            long vertex = destination + (long) i * STRIDE;
            MemoryUtil.memPutFloat(vertex, 0.5f);
            MemoryUtil.memPutFloat(vertex + 4, 0.5f);
            MemoryUtil.memPutInt(vertex + TANGENT_OFFSET, NEUTRAL_TANGENT);
        }
    }

    /**
     * Points attributes {@value #MID_TEX_COORD_LOCATION} and {@value #TANGENT_LOCATION} of the bound vertex array at
     * {@code sideVbo} and enables them; the array buffer binding is restored afterwards.
     */
    static void attachToBoundVertexArray(int sideVbo) {
        final int previous = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, sideVbo);
        GLStateManager.glEnableVertexAttribArray(MID_TEX_COORD_LOCATION);
        GLStateManager.glVertexAttribPointer(MID_TEX_COORD_LOCATION, 2, GL11.GL_FLOAT, false, STRIDE, 0L);
        GLStateManager.glEnableVertexAttribArray(TANGENT_LOCATION);
        GLStateManager.glVertexAttribPointer(TANGENT_LOCATION, 4, GL11.GL_BYTE, true, STRIDE, (long) TANGENT_OFFSET);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
    }
}
