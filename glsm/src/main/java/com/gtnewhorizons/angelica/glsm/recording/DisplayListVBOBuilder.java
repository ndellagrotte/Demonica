package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexedVAO;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.QuadSideAttributeWriter;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.nmemFree;

/**
 * Compiles multiple ByteBuffers of the same Vertex format into a VBO. Any buffer added to the builder will be freed afterward.
 */
public final class DisplayListVBOBuilder {
    // Removes the need for a HashMap. Since it's only an array of 16 values, this should be faster
    private final List<FormatData>[] formats = new List[VertexFlags.BITSET_SIZE];
    private int count;

    public DisplayListVBOBuilder addDraw(VertexFormat format, int drawMode, List<ByteBuffer> buffers) {
        List<FormatData> data = formats[format.getVertexFlags()];
        if (data == null) {
            data = new ArrayList<>();
            formats[format.getVertexFlags()] = data;
        }
        data.add(new FormatData(buffers, drawMode, count));
        count++;
        return this;
    }

    public DisplayListVBOBuilder addDraws(List<AccumulatedDraw> draws) {
        for (AccumulatedDraw draw : draws) {
            addDraw(draw.format, draw.drawMode, draw.drawBuffers);
        }
        return this;
    }

    public DisplayListVBO build() {
        final DisplayListVBO.SubVBO[] vbos = new DisplayListVBO.SubVBO[count];
        final ArrayList<ByteBuffer> allBuffers = new ArrayList<>();
        for (int i = 0; i < VertexFlags.BITSET_SIZE; i++) {
            List<FormatData> formatData = formats[i];
            if (formatData == null) continue;
            final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[i];
            int start = 0;
            final IVertexArrayObject vao = VAOManager.createStorageVAO(format, -1, 0); // drawMode will be ignored
            final IVertexBuffer vbo = vao.getVBO();
            final DisplayListVBO.SideBuffer side = new DisplayListVBO.SideBuffer();
            final int[] vertexCounts = new int[formatData.size()];
            int dataIndex = 0;
            for (FormatData data : formatData) {
                int vertexCount;
                final List<ByteBuffer> drawBuffers = data.buffers;
                if (drawBuffers.size() == 1) {
                    final ByteBuffer buffer = drawBuffers.get(0);
                    allBuffers.add(buffer);
                    vertexCount = format.getVertexCount(buffer);
                } else {
                    allBuffers.addAll(drawBuffers);
                    int size = 0;
                    for (ByteBuffer buffer : drawBuffers) {
                        size += buffer.remaining();
                    }
                    vertexCount = format.getVertexCount(size);
                }
                vertexCounts[dataIndex++] = vertexCount;
                if (data.drawMode == GL11.GL_QUADS) {
                    final IVertexArrayObject indexedVAO = new IndexedVAO(vbo, IndexBuffer.convertQuadsToTrigs(start, start + vertexCount));
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(indexedVAO, GL11.GL_TRIANGLES, 0, vertexCount / 4 * 6, side);
                } else if (data.drawMode == GL11.GL_QUAD_STRIP) {
                    // GL_QUAD_STRIP is removed in core profile; an even-length GL_TRIANGLE_STRIP produces the same quads
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, GL11.GL_TRIANGLE_STRIP, start, vertexCount & ~1, side);
                } else if (data.drawMode == GL11.GL_POLYGON) {
                    // GL_POLYGON is removed in core profile; convert to GL_TRIANGLE_FAN which is equivalent for convex polygons
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, GL11.GL_TRIANGLE_FAN, start, vertexCount, side);
                } else {
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, data.drawMode, start, vertexCount, side);
                }


                start += vertexCount;
            }
            MergedBuffer bigBuffer = mergeAndDelete(allBuffers);
            side.upload(buildSideBuffer(format, formatData, vertexCounts, bigBuffer.address, start));
            vbo.allocate(bigBuffer.buffer, start);
            nmemFree(bigBuffer.address);

            allBuffers.clear();
        }
        return new DisplayListVBO(vbos);
    }

    /**
     * The quad side attributes (mc_midTexCoord and at_tangent, {@link QuadSideAttributeWriter}) of a format group's
     * vertices, computed from the merged vertex data: per GL_QUADS draw the writer's values, the neutral values for
     * any other draw mode in the group. Null when there is nothing to compute: no writer installed yet, a format
     * without a float position, a texture coordinate and a normal (entity models and items have all three; GUI and
     * particle geometry has no normal), or no quads at all.
     */
    private static ByteBuffer buildSideBuffer(VertexFormat format, List<FormatData> formatData, int[] vertexCounts,
                                              long vertices, int totalVertices) {
        final QuadSideAttributeWriter writer = GLSMHooks.quadSideAttributes;
        if (writer == null || totalVertices == 0 || !format.hasTexture() || !format.hasNormals()) {
            return null;
        }
        boolean quads = false;
        for (FormatData data : formatData) {
            quads |= data.drawMode == GL11.GL_QUADS;
        }
        if (!quads) {
            return null;
        }
        int positionOffset = -1;
        int texCoordOffset = -1;
        int offset = 0;
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == VertexFormatElement.Usage.POSITION && element.getType() == VertexFormatElement.Type.FLOAT) {
                positionOffset = offset;
            } else if (element.getUsage() == VertexFormatElement.Usage.PRIMARY_UV && element.getType() == VertexFormatElement.Type.FLOAT) {
                texCoordOffset = offset;
            }
            offset += element.getByteSize();
        }
        if (positionOffset < 0 || texCoordOffset < 0) {
            return null;
        }
        final int stride = format.getVertexSize();
        final ByteBuffer side = memAlloc(totalVertices * QuadSideAttributeWriter.STRIDE);
        final long destination = memAddress0(side);
        int start = 0;
        for (int i = 0; i < vertexCounts.length; i++) {
            final int vertexCount = vertexCounts[i];
            final long out = destination + (long) start * QuadSideAttributeWriter.STRIDE;
            if (formatData.get(i).drawMode == GL11.GL_QUADS) {
                writer.write(vertices + (long) start * stride, stride, positionOffset, texCoordOffset, vertexCount, out);
            } else {
                QuadSideAttributeWriter.writeNeutral(out, vertexCount);
            }
            start += vertexCount;
        }
        return side;
    }

    private static MergedBuffer mergeAndDelete(List<ByteBuffer> buffers) {
        if (buffers.size() == 1) {
            final ByteBuffer buffer = buffers.get(0);
            return new MergedBuffer(buffer, memAddress0(buffer));
        }
        int needed = 0;
        for (ByteBuffer buffer : buffers) {
            needed += buffer.remaining();
        }

        ByteBuffer out = memAlloc(needed);
        final long outAddress = memAddress0(out);
        long dst = outAddress;

        for (ByteBuffer buffer : buffers) {
            final int len = buffer.remaining();
            final long address = memAddress0(buffer);
            memCopy(address, dst, len);
            dst += len;

            nmemFree(address);
        }

        out.limit(needed);
        return new MergedBuffer(out, outAddress);
    }

    private static final class MergedBuffer {
        private final ByteBuffer buffer;
        private final long address;

        private MergedBuffer(ByteBuffer buffer, long address) {
            this.buffer = buffer;
            this.address = address;
        }
    }

    private static final class FormatData {

        private final List<ByteBuffer> buffers;
        private final int drawMode;
        private final int drawIndex;

        public FormatData(List<ByteBuffer> buffers, int drawMode, int drawIndex) {
            this.buffers = buffers;
            this.drawMode = drawMode;
            this.drawIndex = drawIndex;
        }
    }
}
