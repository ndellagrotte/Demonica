package com.demonica.render.vertex;

import net.coderbot.iris.vertices.IrisQuadView;
import org.lwjgl.system.MemoryUtil;

/**
 * A quad of four consecutive vertices in native memory, read through the offsets of their vertex format, for
 * {@link net.coderbot.iris.vertices.NormalHelper}. The terrain encoder's {@code LwjglQuadView} hard-codes Celeritas's
 * layout; this one serves every vanilla format with a float position and a float primary texture coordinate.
 */
final class RawQuadView implements IrisQuadView {
    private long base;
    private int stride;
    private int positionOffset;
    private int texCoordOffset;

    void setup(long firstVertex, int stride, int positionOffset, int texCoordOffset) {
        this.base = firstVertex;
        this.stride = stride;
        this.positionOffset = positionOffset;
        this.texCoordOffset = texCoordOffset;
    }

    private long vertex(int index) {
        return this.base + (long) index * this.stride;
    }

    @Override
    public float x(int index) {
        return MemoryUtil.memGetFloat(vertex(index) + this.positionOffset);
    }

    @Override
    public float y(int index) {
        return MemoryUtil.memGetFloat(vertex(index) + this.positionOffset + 4);
    }

    @Override
    public float z(int index) {
        return MemoryUtil.memGetFloat(vertex(index) + this.positionOffset + 8);
    }

    @Override
    public float u(int index) {
        return MemoryUtil.memGetFloat(vertex(index) + this.texCoordOffset);
    }

    @Override
    public float v(int index) {
        return MemoryUtil.memGetFloat(vertex(index) + this.texCoordOffset + 4);
    }
}
