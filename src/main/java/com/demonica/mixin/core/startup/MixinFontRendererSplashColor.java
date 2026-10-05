package com.demonica.mixin.core.startup;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Splash font renderers draw with colour 0 and expect the current GL colour to survive: Modern Splash sets its font
 * colour with a raw {@code glColor4f} and then calls {@code drawString(text, 0, 0, 0)}. Vanilla turns colour 0 into
 * opaque black, but its {@code GlStateManager} cache already holds black and drops the call, so the splash's colour
 * stays. GLSM tracks both calls, so the text came out black (Actinium issue #31). The colour that was current when
 * the string started is put back once vanilla has set its own.
 */
@Mixin(FontRenderer.class)
public class MixinFontRendererSplashColor {
    @Shadow
    private float red;
    // Vanilla's names are swapped: blue holds the green component and green the blue one.
    @Shadow
    private float blue;
    @Shadow
    private float green;
    @Shadow
    private float alpha;

    @Unique
    private final boolean demonica$splashFont = this.getClass().getName().endsWith("$SplashFontRenderer");
    @Unique
    private boolean demonica$restoreColor;
    @Unique
    private float demonica$savedRed;
    @Unique
    private float demonica$savedGreen;
    @Unique
    private float demonica$savedBlue;
    @Unique
    private float demonica$savedAlpha;

    @Inject(method = "renderString", at = @At("HEAD"), require = 0)
    private void demonica$saveSplashColor(String text, float x, float y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (!this.demonica$splashFont || color != 0 || GLStateManager.isSplashComplete()) {
            return;
        }
        final var current = GLStateManager.getColor();
        // resetColor leaves a negative sentinel, not a colour.
        if (current.getRed() < 0.0F) {
            return;
        }
        this.demonica$savedRed = current.getRed();
        this.demonica$savedGreen = current.getGreen();
        this.demonica$savedBlue = current.getBlue();
        this.demonica$savedAlpha = current.getAlpha();
        this.demonica$restoreColor = true;
    }

    @Inject(
        method = "renderString",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;renderStringAtPos(Ljava/lang/String;Z)V"),
        require = 0
    )
    private void demonica$restoreSplashColor(String text, float x, float y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (!this.demonica$restoreColor) {
            return;
        }
        this.demonica$restoreColor = false;
        this.red = this.demonica$savedRed;
        this.blue = this.demonica$savedGreen;
        this.green = this.demonica$savedBlue;
        this.alpha = this.demonica$savedAlpha;
        GLStateManager.glColor4f(this.demonica$savedRed, this.demonica$savedGreen, this.demonica$savedBlue, this.demonica$savedAlpha);
    }
}
