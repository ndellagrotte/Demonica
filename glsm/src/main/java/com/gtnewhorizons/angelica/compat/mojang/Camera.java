package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Singleton camera instance that tracks the current camera position and orientation.
 */
@Getter
public class Camera {
    public static final Camera INSTANCE = new Camera();

    private final Vector3d pos = new Vector3d();
    private final Vector3d entityPos = new Vector3d();
    private BlockPos blockPos = BlockPos.ORIGIN;
    /**
     * The render origin relative to the interpolated feet, the origin of vanilla's model-view: pos = feet + origin.
     * gbufferModelView, the terrain draw and the shadow pass are all anchored on it.
     */
    private final Vector3f origin = new Vector3f();
    private final Matrix4f inverseModelView = new Matrix4f();

    private float pitch;
    private float yaw;
    private boolean isSleeping;
    private boolean thirdPerson;
    private float partialTicks;

    private Camera() {}

    /**
     * Updates the camera position and orientation based on the current entity and partial ticks.
     *
     * @param entity The entity to track (usually the render view entity)
     * @param partialTicks The partial tick time for interpolation
     */
    public void update(EntityLivingBase entity, float partialTicks) {
        this.partialTicks = partialTicks;
        this.isSleeping = entity.isPlayerSleeping();

        // The interpolated feet, the origin of vanilla's model-view; entityPos is the eye above them.
        final double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        final double eyeHeight = entity.getEyeHeight();

        entityPos.set(entityX, entityY + eyeHeight, entityZ);

        thirdPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView > 0;

        if (thirdPerson) {
            // The real camera: vanilla's model-view (captured at the end of updateRenderInfo) maps it to the view-space
            // origin. orientCamera's eye height, the third-person distance and view bobbing's translation are all in
            // it, so the camera bobs while walking in F5, and gbufferModelViewInverse still maps view space onto it
            // exactly.
            inverseModelView.set(RenderingState.INSTANCE.getModelViewMatrix()).invert();
            inverseModelView.transformPosition(origin.set(0f, 0f, 0f));
        } else {
            // The eye, without view bobbing, as before. This is wrong while sleeping (orientCamera lifts and turns the
            // camera there) and under camera mods that translate the first-person model-view; both were already off
            // with the old eye anchor.
            origin.set(0f, (float) eyeHeight, 0f);
        }

        final double camX = entityX + origin.x;
        final double camY = entityY + origin.y;
        final double camZ = entityZ + origin.z;

        pos.set(camX, camY, camZ);
        blockPos = new BlockPos(MathHelper.floor(camX), MathHelper.floor(camY), MathHelper.floor(camZ));
        pitch = entity.cameraPitch;
        yaw = entity.rotationYaw;
    }
}
