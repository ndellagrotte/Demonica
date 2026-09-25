package com.demonica.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen;

import java.util.Set;

/**
 * The mod list's Config button for Demonica ({@code @Mod(guiFactory)}): opens Video Settings over the mod list. The
 * screen is Celeritas's, the same one the Video Settings button opens, with Demonica's settings in its pages.
 */
public final class DemonicaGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public Set<IModGuiFactory.RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new CeleritasVideoOptionsScreen(parentScreen);
    }
}
