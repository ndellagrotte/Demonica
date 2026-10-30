package com.demonica.mixin.mod.hbm;

import com.demonica.compat.hbm.HbmInstancedParticlesCompat;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the shader rule to HBM's {@code instancedParticles} flag before HBM reads it.
 *
 * <p>The client tick runs before that tick's packets spawn particles, so the effect handlers see
 * the synced value; the render-last hook covers the renderer itself (see
 * {@link HbmInstancedParticlesCompat}).</p>
 */
@Mixin(targets = "com.hbm.particle_instanced.InstancedParticleRenderer", remap = false)
public abstract class MixinInstancedParticleRenderer {
    @Inject(
        method = "clientTick(Lnet/minecraftforge/fml/common/gameevent/TickEvent$ClientTickEvent;)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void demonica$syncOnTick(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        HbmInstancedParticlesCompat.sync();
    }

    @Inject(
        method = "renderLast(Lnet/minecraftforge/client/event/RenderWorldLastEvent;)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void demonica$syncOnRender(RenderWorldLastEvent event, CallbackInfo ci) {
        HbmInstancedParticlesCompat.sync();
    }
}
