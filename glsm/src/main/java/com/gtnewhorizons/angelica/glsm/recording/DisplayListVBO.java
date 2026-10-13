package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.QuadSideAttributeWriter;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * A class that stores multiple vertex formats & their corresponding buffers.
 */
public final class DisplayListVBO {

    private final SubVBO[] vbos;

    DisplayListVBO(SubVBO[] vbos) {
        this.vbos = vbos;
    }

    public void delete() {
        for (SubVBO vbo : vbos) {
            vbo.delete();
        }
    }

    public SubVBO[] getVBOs() {
        return vbos;
    }

    public void render(int index) {
        vbos[index].render();
    }

    public static final class SubVBO {

        private final IVertexArrayObject vao;
        private final int drawMode;
        private final int start;
        private final int count;
        private final SideBuffer side;
        private boolean sideAttached;

        public SubVBO(IVertexArrayObject vao, int drawMode, int start, int count) {
            this(vao, drawMode, start, count, null);
        }

        public SubVBO(IVertexArrayObject vao, int drawMode, int start, int count, SideBuffer side) {
            this.vao = vao;
            this.drawMode = drawMode;
            this.start = start;
            this.count = count;
            this.side = side;
        }

        public void delete() {
            vao.delete();
            if (side != null) {
                side.delete();
            }
        }

        public void render() {
            vao.bind();
            if (side != null && !sideAttached) {
                // The vertex array's own pointers were set up by its first bind; the side pointers are VAO state too,
                // so once is enough.
                side.attachToBoundVertexArray();
                sideAttached = true;
            }
            vao.draw(drawMode, start, count);
            vao.unbind();
        }

        public IVertexArrayObject getVAO() {
            return vao;
        }
    }

    /**
     * One format group's buffer of quad side attributes ({@link QuadSideAttributeWriter}), shared by the group's
     * SubVBOs and deleted with the first of them; empty when the group has none.
     */
    public static final class SideBuffer {
        private int vbo;

        /** Uploads {@code data} (freed here) as a static buffer; null leaves the side buffer empty. */
        void upload(ByteBuffer data) {
            if (data == null) {
                return;
            }
            final int previous = GLStateManager.getBoundVBO();
            this.vbo = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vbo);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
            memFree(data);
        }

        void attachToBoundVertexArray() {
            if (this.vbo != 0) {
                QuadSideAttributeWriter.attachToBoundVertexArray(this.vbo);
            }
        }

        void delete() {
            if (this.vbo != 0) {
                GLStateManager.glDeleteBuffers(this.vbo);
                this.vbo = 0;
            }
        }
    }
}
