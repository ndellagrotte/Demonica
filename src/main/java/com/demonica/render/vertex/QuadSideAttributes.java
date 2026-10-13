package com.demonica.render.vertex;

import com.gtnewhorizons.angelica.glsm.hooks.QuadSideAttributeWriter;
import net.coderbot.iris.vertices.NormalHelper;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/**
 * The {@link QuadSideAttributeWriter}: per quad, the mid-texture coordinate is the mean of the four texture
 * coordinates and the tangent comes from {@link NormalHelper#computeTangent} against the face normal of the quad's
 * positions, as the Celeritas terrain encoder computes them for blocks. Vanilla quads (ModelBox faces, items, item
 * frames) wind consistently with their normal, so the face normal is the right reference.
 *
 * <p>A quad whose texture coordinates are degenerate has no tangent (the math divides by zero and the packed bytes
 * come out as zero); it gets the neutral tangent instead, so the shader's normalize never sees a zero vector.</p>
 */
public final class QuadSideAttributes implements QuadSideAttributeWriter {
    public static final QuadSideAttributes INSTANCE = new QuadSideAttributes();

    private final RawQuadView quad = new RawQuadView();
    private final Vector3f normal = new Vector3f();

    private QuadSideAttributes() {
    }

    /**
     * The offsets of the position and the primary texture coordinate in a vanilla format this writer can read, or null
     * when the format has no three-float position, no two-float texture coordinate on unit 0, or no normal (particles
     * and GUI geometry have none, and no pack lights them with a tangent frame).
     *
     * @return {@code {positionOffset, texCoordOffset}} or null
     */
    public static int @Nullable [] layout(VertexFormat format) {
        int positionOffset = -1;
        int texCoordOffset = -1;
        boolean normal = false;
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            switch (element.getUsage()) {
                case POSITION -> {
                    if (element.getType() == VertexFormatElement.EnumType.FLOAT && element.getElementCount() == 3) {
                        positionOffset = format.getOffset(i);
                    }
                }
                case UV -> {
                    if (element.getIndex() == 0 && element.getType() == VertexFormatElement.EnumType.FLOAT
                        && element.getElementCount() == 2) {
                        texCoordOffset = format.getOffset(i);
                    }
                }
                case NORMAL -> normal = true;
                default -> {
                }
            }
        }
        if (positionOffset < 0 || texCoordOffset < 0 || !normal) {
            return null;
        }
        return new int[] {positionOffset, texCoordOffset};
    }

    @Override
    public void write(long source, int stride, int positionOffset, int texCoordOffset, int vertexCount, long destination) {
        final int quads = vertexCount / 4;
        for (int q = 0; q < quads; q++) {
            this.quad.setup(source + (long) q * 4 * stride, stride, positionOffset, texCoordOffset);
            final float midU = (this.quad.u(0) + this.quad.u(1) + this.quad.u(2) + this.quad.u(3)) * 0.25f;
            final float midV = (this.quad.v(0) + this.quad.v(1) + this.quad.v(2) + this.quad.v(3)) * 0.25f;
            NormalHelper.computeFaceNormal(this.normal, this.quad);
            int tangent = NormalHelper.computeTangent(this.normal.x(), this.normal.y(), this.normal.z(), this.quad);
            if ((tangent & 0xFFFFFF) == 0) {
                tangent = NEUTRAL_TANGENT;
            }
            final long out = destination + (long) q * 4 * STRIDE;
            for (int v = 0; v < 4; v++) {
                final long vertex = out + (long) v * STRIDE;
                MemoryUtil.memPutFloat(vertex, midU);
                MemoryUtil.memPutFloat(vertex + 4, midV);
                MemoryUtil.memPutInt(vertex + TANGENT_OFFSET, tangent);
            }
        }
        final int written = quads * 4;
        if (written < vertexCount) {
            QuadSideAttributeWriter.writeNeutral(destination + (long) written * STRIDE, vertexCount - written);
        }
    }
}
