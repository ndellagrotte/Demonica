package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.states.Color4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies how the GLSM current color is normalized before it reaches an FFP shader uniform or a captured vertex.
 * {@code GLStateManager.clearCurrentColor()} (the redirect target of vanilla {@code GlStateManager.resetColor()})
 * marks the cached color with (-1,-1,-1,-1); uploading that raw value makes every vertex-color-less draw fully
 * transparent (negative alpha clamps to 0 in GLSL), so the sentinel becomes GL's default opaque white. Every other
 * color is clamped to [0,1] per component, as fixed-function GL does when it uses the current color: AsmodeusCore's
 * sky sets a negative alpha on its galaxy images by day and a negative RGB on its sky dome at night, and both must
 * draw as nothing or black, not white.
 *
 * <p>GLStateManager itself cannot be loaded without a GL context (its static
 * initializers query the backend), so the sentinel state is reproduced directly.
 */
class UniformColorSanitizeTest {

    @Test
    void dirtySentinelColorIsNormalizedToOpaqueWhite() {
        Color4 result = Uniforms.sanitizeUniformColor(new Color4(-1.0F, -1.0F, -1.0F, -1.0F));

        assertEquals(1.0F, result.getRed());
        assertEquals(1.0F, result.getGreen());
        assertEquals(1.0F, result.getBlue());
        assertEquals(1.0F, result.getAlpha());
    }

    @Test
    void negativeAlphaClampsToTransparent() {
        // AsmodeusCore's galaxy band by day: glColor4f(1, 1, 1, starBrightness - 0.2F) with starBrightness 0.
        Color4 result = Uniforms.sanitizeUniformColor(new Color4(1.0F, 1.0F, 1.0F, -0.2F));

        assertEquals(1.0F, result.getRed());
        assertEquals(1.0F, result.getGreen());
        assertEquals(1.0F, result.getBlue());
        assertEquals(0.0F, result.getAlpha());
    }

    @Test
    void negativeRgbClampsToBlack() {
        // AsmodeusCore's sky dome at night: glColor3f(skyColor - posY / 400) with a near-black sky color.
        Color4 result = Uniforms.sanitizeUniformColor(new Color4(-0.14F, -0.14F, -0.11F, 1.0F));

        assertEquals(0.0F, result.getRed());
        assertEquals(0.0F, result.getGreen());
        assertEquals(0.0F, result.getBlue());
        assertEquals(1.0F, result.getAlpha());
    }

    @Test
    void componentsAboveOneClampToOne() {
        // Star brightness plus the height term above y=150 exceeds 1.
        Color4 result = Uniforms.sanitizeUniformColor(new Color4(0.5F, 1.5F, 1.0F, 1.2F));

        assertEquals(0.5F, result.getRed());
        assertEquals(1.0F, result.getGreen());
        assertEquals(1.0F, result.getBlue());
        assertEquals(1.0F, result.getAlpha());
    }

    @Test
    void onlyTheFullSentinelIsTreatedAsDirty() {
        // clearCurrentColor writes all four components; a lone -1 alpha is a real (if odd) color and clamps.
        Color4 result = Uniforms.sanitizeUniformColor(new Color4(1.0F, 1.0F, 1.0F, -1.0F));

        assertEquals(1.0F, result.getRed());
        assertEquals(0.0F, result.getAlpha());
    }

    @Test
    void zeroAlphaColorIsNotTreatedAsDirtySentinel() {
        Color4 result = Uniforms.sanitizeUniformColor(new Color4(0.0F, 0.0F, 0.0F, 0.0F));

        assertSame(result, Uniforms.sanitizeUniformColor(result));
        assertEquals(0.0F, result.getAlpha());
    }

    @Test
    void validColorPassesThroughUnchanged() {
        Color4 color = new Color4(0.5F, 0.5F, 0.5F, 0.5F);

        assertSame(color, Uniforms.sanitizeUniformColor(color));
    }

    @Test
    void clamp01HandlesNaN() {
        assertEquals(0.0F, Uniforms.clamp01(Float.NaN));
        assertEquals(0.0F, Uniforms.clamp01(-3.0F));
        assertEquals(1.0F, Uniforms.clamp01(7.0F));
        assertEquals(0.25F, Uniforms.clamp01(0.25F));
    }
}
