package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;

/**
 * A 4x4 matrix uniform that takes {@code org.joml} matrices. Celeritas's own {@code GlUniformMatrix4f} takes the
 * JOML that its mod jar relocates, which Demonica does not compute with (see {@code CeleritasJoml}).
 */
public class GlUniformJomlMatrix4f extends GlUniform<Matrix4fc> {
    public GlUniformJomlMatrix4f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix4fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final FloatBuffer buffer = stack.mallocFloat(16);
            value.get(buffer);
            GLStateManager.glUniformMatrix4(this.index, false, buffer);
        }
    }
}
