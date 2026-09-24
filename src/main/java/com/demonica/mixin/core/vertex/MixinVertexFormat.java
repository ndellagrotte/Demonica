package com.demonica.mixin.core.vertex;

import com.demonica.render.vertex.FastVertexLayout;
import com.demonica.render.vertex.FastVertexLayoutCalculator;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Maintains the pre-computed element offsets and the element-advance ring on every
 * vertex format so the immediate-mode write path can consume them as plain arrays.
 *
 * <p>{@code addElement} is the only path that appends elements (the copy constructor and
 * the format constants both route through it, including its duplicate-position early
 * return, which re-runs the rebuild with an unchanged list), and {@code clear} is the
 * only path that empties them; both are covered so the arrays can never diverge from the
 * element list.
 */
@Mixin(VertexFormat.class)
public abstract class MixinVertexFormat implements FastVertexLayout {
    @Shadow
    private List<VertexFormatElement> elements;

    @Unique
    private int[] demonica$offsets = new int[0];

    @Unique
    private int[] demonica$nextIndices = new int[0];

    @Inject(method = "addElement", at = @At("TAIL"))
    private void demonica$rebuildLayout(VertexFormatElement element,
                                        CallbackInfoReturnable<VertexFormat> cir) {
        FastVertexLayoutCalculator.Layout layout =
            FastVertexLayoutCalculator.compute(this.elements);
        this.demonica$offsets = layout.offsets();
        this.demonica$nextIndices = layout.nextIndices();
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void demonica$clearLayout(CallbackInfo ci) {
        this.demonica$offsets = new int[0];
        this.demonica$nextIndices = new int[0];
    }

    @Override
    public int[] demonica$offsets() {
        return this.demonica$offsets;
    }

    @Override
    public int[] demonica$nextIndices() {
        return this.demonica$nextIndices;
    }
}
