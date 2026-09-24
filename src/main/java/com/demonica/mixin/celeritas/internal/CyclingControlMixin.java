package com.demonica.mixin.celeritas.internal;

import com.demonica.celeritas.guard.Patch;
import com.demonica.celeritas.guard.PatchGroup;
import com.demonica.celeritas.options.CyclingControlAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.taumc.celeritas.api.options.control.CyclingControl;

/**
 * O1 (docs/celeritas/patches/O1.md): the values a cycling control offers, which Reese's Sodium Options needs to draw
 * and step its own cycling row.
 */
@Patch(value = "O1", group = PatchGroup.OPTIONS)
@Mixin(value = CyclingControl.class, remap = false, priority = 1100)
public abstract class CyclingControlMixin<T> implements CyclingControlAccess {
    @Shadow
    @Final
    private T[] allowedValues;

    @Override
    public Object[] demonica$getAllowedValues() {
        return this.allowedValues;
    }
}
