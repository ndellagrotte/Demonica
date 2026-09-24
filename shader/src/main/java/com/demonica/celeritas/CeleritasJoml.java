package com.demonica.celeritas;

import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.embeddedt.embeddium.impl.shadow.joml.Matrix4f;
import org.embeddedt.embeddium.impl.shadow.joml.Vector3d;

/**
 * The JOML seam. Celeritas's mod jar relocates JOML to {@code org.embeddedt.embeddium.impl.shadow.joml}, so every
 * Celeritas method with a JOML type in its signature takes the relocated classes, while Demonica computes with the
 * {@code org.joml} that Cleanroom ships. Conversions happen here. Only the classes on the allow-list in
 * {@code JomlBoundaryTest} may name the relocated package; everything else goes through this helper.
 */
public final class CeleritasJoml {
    private CeleritasJoml() {
    }

    /** A viewport for {@code frustum} with its camera at {@code x, y, z}. */
    public static Viewport viewport(Frustum frustum, double x, double y, double z) {
        return new Viewport(frustum, new Vector3d(x, y, z));
    }

    /** Copies {@code source} into a new matrix of Celeritas's relocated JOML. */
    public static Matrix4f toCeleritas(org.joml.Matrix4fc source) {
        return new Matrix4f(
            source.m00(), source.m01(), source.m02(), source.m03(),
            source.m10(), source.m11(), source.m12(), source.m13(),
            source.m20(), source.m21(), source.m22(), source.m23(),
            source.m30(), source.m31(), source.m32(), source.m33());
    }

    /** Copies a matrix of Celeritas's relocated JOML into {@code dest}, and returns {@code dest}. */
    public static org.joml.Matrix4f fromCeleritas(org.embeddedt.embeddium.impl.shadow.joml.Matrix4fc source, org.joml.Matrix4f dest) {
        return dest.set(
            source.m00(), source.m01(), source.m02(), source.m03(),
            source.m10(), source.m11(), source.m12(), source.m13(),
            source.m20(), source.m21(), source.m22(), source.m23(),
            source.m30(), source.m31(), source.m32(), source.m33());
    }
}
