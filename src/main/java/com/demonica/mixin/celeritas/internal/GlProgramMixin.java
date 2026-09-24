package com.demonica.mixin.celeritas.internal;

import net.coderbot.iris.gl.blending.DepthColorStorage;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * S17 (docs/celeritas/patches/S17.md): while a pack's pipeline overrides shaders, Iris treats a bind of a program it
 * does not know as a mod's own shader taking over the pass (IrisGLSMBridge's program-change listener). The programs
 * Celeritas links, both its own and the pack's terrain programs Iris links through {@code GlProgram.builder}, are
 * not foreign, so each is registered with {@link DepthColorStorage} for as long as it lives.
 */
@Mixin(value = GlProgram.class, remap = false, priority = 1100)
public abstract class GlProgramMixin extends GlObject {
    @Inject(method = "<init>(ILjava/util/function/Function;)V", at = @At("TAIL"))
    private void demonica$registerOwnedProgram(int program, Function<ShaderBindingContext, ?> interfaceFactory, CallbackInfo ci) {
        DepthColorStorage.registerOwnedProgram(program);
    }

    @Inject(method = "destroyInternal()V", at = @At("HEAD"))
    private void demonica$unregisterOwnedProgram(CallbackInfo ci) {
        DepthColorStorage.unregisterOwnedProgram(this.handle());
    }
}
