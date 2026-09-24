package com.demonica.mixin.core;

import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * The camera state {@code ActiveRenderInfo} keeps in static fields, for compat that renders a second camera within a
 * frame and must put the first one back (iChunUtil's world portals). The accessor names carry Demonica's prefix:
 * Celeritas has its own accessor for the two matrices on the same class.
 */
@Mixin(ActiveRenderInfo.class)
public interface AccessorActiveRenderInfo {
    @Accessor("PROJECTION")
    static FloatBuffer demonica$getProjectionMatrix() {
        throw new AssertionError();
    }

    @Accessor("MODELVIEW")
    static FloatBuffer demonica$getModelViewMatrix() {
        throw new AssertionError();
    }

    @Accessor("OBJECTCOORDS")
    static FloatBuffer demonica$getObjectCoords() {
        throw new AssertionError();
    }

    @Accessor("VIEWPORT")
    static IntBuffer demonica$getViewport() {
        throw new AssertionError();
    }

    @Accessor("position")
    static Vec3d demonica$getPosition() {
        throw new AssertionError();
    }

    @Accessor("position")
    static void demonica$setPosition(Vec3d position) {
        throw new AssertionError();
    }

    @Accessor("rotationX")
    static float demonica$getRotationX() {
        throw new AssertionError();
    }

    @Accessor("rotationX")
    static void demonica$setRotationX(float rotationX) {
        throw new AssertionError();
    }

    @Accessor("rotationXZ")
    static float demonica$getRotationXZ() {
        throw new AssertionError();
    }

    @Accessor("rotationXZ")
    static void demonica$setRotationXZ(float rotationXZ) {
        throw new AssertionError();
    }

    @Accessor("rotationZ")
    static float demonica$getRotationZ() {
        throw new AssertionError();
    }

    @Accessor("rotationZ")
    static void demonica$setRotationZ(float rotationZ) {
        throw new AssertionError();
    }

    @Accessor("rotationYZ")
    static float demonica$getRotationYZ() {
        throw new AssertionError();
    }

    @Accessor("rotationYZ")
    static void demonica$setRotationYZ(float rotationYZ) {
        throw new AssertionError();
    }

    @Accessor("rotationXY")
    static float demonica$getRotationXY() {
        throw new AssertionError();
    }

    @Accessor("rotationXY")
    static void demonica$setRotationXY(float rotationXY) {
        throw new AssertionError();
    }
}
