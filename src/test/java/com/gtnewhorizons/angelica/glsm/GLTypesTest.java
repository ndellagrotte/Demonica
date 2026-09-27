package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GLTypesTest {

    @Test
    void alphaSizedFormatsPromoteToRgbaOfTheSameSize() {
        assertEquals(GL11.GL_RGBA4, GLTypes.promoteAlphaFormat(GL11.GL_ALPHA4));
        assertEquals(GL11.GL_RGBA8, GLTypes.promoteAlphaFormat(GL11.GL_ALPHA8));
        assertEquals(GL11.GL_RGBA12, GLTypes.promoteAlphaFormat(GL11.GL_ALPHA12));
        assertEquals(GL11.GL_RGBA16, GLTypes.promoteAlphaFormat(GL11.GL_ALPHA16));
    }

    @Test
    void otherFormatsPassThrough() {
        assertEquals(GL11.GL_ALPHA, GLTypes.promoteAlphaFormat(GL11.GL_ALPHA));
        assertEquals(GL11.GL_RGBA8, GLTypes.promoteAlphaFormat(GL11.GL_RGBA8));
        assertEquals(GL11.GL_RGB16, GLTypes.promoteAlphaFormat(GL11.GL_RGB16));
        assertEquals(GL30.GL_RGBA16F, GLTypes.promoteAlphaFormat(GL30.GL_RGBA16F));
        assertEquals(GL11.GL_DEPTH_COMPONENT, GLTypes.promoteAlphaFormat(GL11.GL_DEPTH_COMPONENT));
    }
}
