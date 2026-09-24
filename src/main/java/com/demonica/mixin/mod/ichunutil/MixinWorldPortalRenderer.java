package com.demonica.mixin.mod.ichunutil;

import com.demonica.compat.ichunutil.PortalRenderState;
import com.demonica.mixin.core.AccessorActiveRenderInfo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.ichun.mods.ichunutil.common.module.worldportals.client.render.WorldPortalRenderer;
import me.ichun.mods.ichunutil.common.module.worldportals.common.portal.WorldPortal;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldPortalRenderer.class, remap = false)
public class MixinWorldPortalRenderer {
    @WrapMethod(
        method = "renderWorldPortal(Lnet/minecraft/client/Minecraft;"
            + "Lme/ichun/mods/ichunutil/common/module/worldportals/common/portal/WorldPortal;"
            + "Lnet/minecraft/entity/Entity;[F[FF)V"
    )
    private static void demonica$preserveRenderState(
        Minecraft minecraft,
        WorldPortal portal,
        Entity entity,
        float[] position,
        float[] rotation,
        float partialTicks,
        Operation<Void> original
    ) {
        Vec3d cameraPosition = AccessorActiveRenderInfo.demonica$getPosition();
        float rotationX = AccessorActiveRenderInfo.demonica$getRotationX();
        float rotationXZ = AccessorActiveRenderInfo.demonica$getRotationXZ();
        float rotationZ = AccessorActiveRenderInfo.demonica$getRotationZ();
        float rotationYZ = AccessorActiveRenderInfo.demonica$getRotationYZ();
        float rotationXY = AccessorActiveRenderInfo.demonica$getRotationXY();

        try {
            PortalRenderState.preserve(
                AccessorActiveRenderInfo.demonica$getProjectionMatrix(),
                AccessorActiveRenderInfo.demonica$getModelViewMatrix(),
                AccessorActiveRenderInfo.demonica$getObjectCoords(),
                AccessorActiveRenderInfo.demonica$getViewport(),
                () -> original.call(minecraft, portal, entity, position, rotation, partialTicks)
            );
        } finally {
            AccessorActiveRenderInfo.demonica$setPosition(cameraPosition);
            AccessorActiveRenderInfo.demonica$setRotationX(rotationX);
            AccessorActiveRenderInfo.demonica$setRotationXZ(rotationXZ);
            AccessorActiveRenderInfo.demonica$setRotationZ(rotationZ);
            AccessorActiveRenderInfo.demonica$setRotationYZ(rotationYZ);
            AccessorActiveRenderInfo.demonica$setRotationXY(rotationXY);
        }
    }
}
