package com.demonica.diagnostics.mixin;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.util.List;

/** The dev harness's {@code press} step: a screen's buttons, and the click handler a real click reaches. */
@Mixin(GuiScreen.class)
public interface GuiScreenAccessor {
    @Accessor("buttonList")
    List<GuiButton> demonica$getButtonList();

    @Invoker("actionPerformed")
    void demonica$actionPerformed(GuiButton button) throws IOException;
}
