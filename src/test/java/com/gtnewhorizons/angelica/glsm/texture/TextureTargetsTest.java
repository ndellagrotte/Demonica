package com.gtnewhorizons.angelica.glsm.texture;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GLSM's deferred texture delete shrinks the texture as a GL_TEXTURE_2D, so only a name GLSM saw bound to that target
 * may take it. Kirino's glyph atlas is a GL_TEXTURE_2D_ARRAY created with DSA, which GLSM never sees bound: shrinking
 * it as 2D corrupted the vanilla font texture that was really bound.
 */
class TextureTargetsTest {

    @Test
    void only2DTexturesAreDeferrable() {
        TextureTargets targets = new TextureTargets();
        targets.record(1, GL11.GL_TEXTURE_2D);
        targets.record(2, GL30.GL_TEXTURE_2D_ARRAY);
        targets.record(3, GL12.GL_TEXTURE_3D);
        targets.record(4, GL13.GL_TEXTURE_CUBE_MAP);

        assertTrue(TextureTargets.isDeferrable(targets.remove(1)));
        assertFalse(TextureTargets.isDeferrable(targets.remove(2)));
        assertFalse(TextureTargets.isDeferrable(targets.remove(3)));
        assertFalse(TextureTargets.isDeferrable(targets.remove(4)));
    }

    @Test
    void aNameNeverSeenBoundIsUnknownAndNotDeferrable() {
        TextureTargets targets = new TextureTargets();
        // Kirino on a 4.5+ context: glCreateTextures + glBindTextureUnit, then glDeleteTextures through GLSM.
        assertEquals(TextureTargets.UNKNOWN, targets.remove(42));
        assertFalse(TextureTargets.isDeferrable(TextureTargets.UNKNOWN));
    }

    @Test
    void removeForgetsAndTheLastBindWins() {
        TextureTargets targets = new TextureTargets();
        targets.record(7, GL11.GL_TEXTURE_2D);
        assertEquals(GL11.GL_TEXTURE_2D, targets.remove(7));
        assertEquals(TextureTargets.UNKNOWN, targets.remove(7));

        targets.record(8, GL11.GL_TEXTURE_2D);
        targets.record(8, GL30.GL_TEXTURE_2D_ARRAY);
        assertEquals(GL30.GL_TEXTURE_2D_ARRAY, targets.remove(8));
    }

    @Test
    void nameZeroIsNeverRecorded() {
        TextureTargets targets = new TextureTargets();
        targets.record(0, GL11.GL_TEXTURE_2D);
        assertEquals(TextureTargets.UNKNOWN, targets.remove(0));
    }
}
