package com.demonica.gui;

import com.demonica.gui.options.OptionsScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen;

import java.util.Set;

/**
 * The mod list's Config button for Demonica ({@code @Mod(guiFactory)}): opens Video Settings over the mod list. The
 * screen is Celeritas's, which {@link OptionsScreens} swaps for Reese's Sodium Options on display, exactly as it does
 * for the Video Settings button.
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
