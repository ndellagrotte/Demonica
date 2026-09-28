package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * GL pixel/attrib type sizing and naming used by display-list recording and its diagnostics,
 * and the texture internal-format promotion every texture upload goes through.
 */
public final class GLTypes {

    private GLTypes() {}

    /**
     * Alpha-sized internal formats become RGBA of the same size, because drivers disagree on
     * how they size ALPHA formats.
     */
    public static int promoteAlphaFormat(int internalformat) {
        return switch (internalformat) {
            case GL11.GL_ALPHA4 -> GL11.GL_RGBA4;
            case GL11.GL_ALPHA8 -> GL11.GL_RGBA8;
            case GL11.GL_ALPHA12 -> GL11.GL_RGBA12;
            case GL11.GL_ALPHA16 -> GL11.GL_RGBA16;
            default -> internalformat;
        };
    }

    public static int sizeBytes(int glType) {
        return switch (glType) {
            case GL11.GL_BYTE, GL11.GL_UNSIGNED_BYTE -> 1;
            case GL11.GL_SHORT, GL11.GL_UNSIGNED_SHORT, GL30.GL_HALF_FLOAT -> 2;
            case GL11.GL_INT, GL11.GL_UNSIGNED_INT, GL11.GL_FLOAT -> 4;
            case GL11.GL_DOUBLE -> 8;
            default -> throw new IllegalArgumentException("Unknown GL type: 0x" + Integer.toHexString(glType));
        };
    }

    public static int packedTexelBytes(int glType) {
        return switch (glType) {
            case GL12.GL_UNSIGNED_BYTE_3_3_2, GL12.GL_UNSIGNED_BYTE_2_3_3_REV -> 1;
            case GL12.GL_UNSIGNED_SHORT_5_6_5, GL12.GL_UNSIGNED_SHORT_5_6_5_REV,
                 GL12.GL_UNSIGNED_SHORT_4_4_4_4, GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV,
                 GL12.GL_UNSIGNED_SHORT_5_5_5_1, GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV -> 2;
            case GL12.GL_UNSIGNED_INT_8_8_8_8, GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                 GL12.GL_UNSIGNED_INT_10_10_10_2, GL12.GL_UNSIGNED_INT_2_10_10_10_REV,
                 GL30.GL_UNSIGNED_INT_24_8, GL30.GL_UNSIGNED_INT_10F_11F_11F_REV,
                 GL30.GL_UNSIGNED_INT_5_9_9_9_REV -> 4;
            case GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV -> 8;
            default -> 0;
        };
    }

    public static String name(int glType) {
        return switch (glType) {
            case GL11.GL_BYTE -> "BYTE";
            case GL11.GL_UNSIGNED_BYTE -> "UNSIGNED_BYTE";
            case GL11.GL_SHORT -> "SHORT";
            case GL11.GL_UNSIGNED_SHORT -> "UNSIGNED_SHORT";
            case GL11.GL_INT -> "INT";
            case GL11.GL_UNSIGNED_INT -> "UNSIGNED_INT";
            case GL11.GL_FLOAT -> "FLOAT";
            case GL11.GL_DOUBLE -> "DOUBLE";
            case GL30.GL_HALF_FLOAT -> "HALF_FLOAT";
            case GL12.GL_UNSIGNED_BYTE_3_3_2 -> "UNSIGNED_BYTE_3_3_2";
            case GL12.GL_UNSIGNED_SHORT_4_4_4_4 -> "UNSIGNED_SHORT_4_4_4_4";
            case GL12.GL_UNSIGNED_SHORT_5_5_5_1 -> "UNSIGNED_SHORT_5_5_5_1";
            case GL12.GL_UNSIGNED_INT_8_8_8_8 -> "UNSIGNED_INT_8_8_8_8";
            case GL12.GL_UNSIGNED_INT_8_8_8_8_REV -> "UNSIGNED_INT_8_8_8_8_REV";
            case GL12.GL_UNSIGNED_INT_10_10_10_2 -> "UNSIGNED_INT_10_10_10_2";
            case GL12.GL_UNSIGNED_INT_2_10_10_10_REV -> "UNSIGNED_INT_2_10_10_10_REV";
            default -> String.format("0x%X", glType);
        };
    }
}
