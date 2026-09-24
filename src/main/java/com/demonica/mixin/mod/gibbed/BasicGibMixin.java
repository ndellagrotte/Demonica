package com.demonica.mixin.mod.gibbed;

import com.demonica.compat.gibbed.DemonicaModelRenderer;
import com.demonica.debug.DemonicaDiagnostics;
import fonnymunkey.gibbed.util.IModelRenderer;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "fonnymunkey.gibbed.client.gib.BasicGib", remap = false)
public abstract class BasicGibMixin {
    @Redirect(
        method = "render(Lfonnymunkey/gibbed/client/gib/RenderGib;Lfonnymunkey/gibbed/client/gib/EntityGib;DDDF[F)V",
        at = @At(
            value = "INVOKE",
            target = "Lfonnymunkey/gibbed/util/IModelRenderer;gibbed$renderSingular(F)V"
        )
    )
    private void demonica$renderSingleGibPart(IModelRenderer renderer, float scale) {
        if (renderer instanceof DemonicaModelRenderer) {
            DemonicaDiagnostics.recordGibbedRenderPath("single-immediate");
            ((DemonicaModelRenderer) renderer).demonica$renderGibbedSingleModelPart(scale);
            return;
        }

        DemonicaDiagnostics.recordGibbedRenderPath("single-fallback");
        renderer.gibbed$renderSingular(scale);
    }

    @Redirect(
        method = "render(Lfonnymunkey/gibbed/client/gib/RenderGib;Lfonnymunkey/gibbed/client/gib/EntityGib;DDDF[F)V",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V",
            remap = true
        )
    )
    private void demonica$renderGibPartWithChildren(ModelRenderer renderer, float scale) {
        if (renderer instanceof DemonicaModelRenderer) {
            DemonicaDiagnostics.recordGibbedRenderPath("full-immediate");
            ((DemonicaModelRenderer) renderer).demonica$renderGibbedModel(scale);
            return;
        }

        DemonicaDiagnostics.recordGibbedRenderPath("full-fallback");
        renderer.render(scale);
    }
}
