package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.options.SliderControlAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.SliderControl;

/**
 * O1 (docs/celeritas/patches/O1.md): the slider's range, step and formatter, which Reese's Sodium Options needs to
 * draw its own slider row.
 */
@Mixin(value = SliderControl.class, remap = false, priority = 1100)
public abstract class SliderControlMixin implements SliderControlAccess {
    @Shadow
    @Final
    private int min;

    @Shadow
    @Final
    private int max;

    @Shadow
    @Final
    private int interval;

    @Shadow
    @Final
    private ControlValueFormatter mode;

    @Override
    public int demonica$getMin() {
        return this.min;
    }

    @Override
    public int demonica$getMax() {
        return this.max;
    }

    @Override
    public int demonica$getInterval() {
        return this.interval;
    }

    @Override
    public ControlValueFormatter demonica$getFormatter() {
        return this.mode;
    }
}
