package com.demonica.gui.options;

import com.demonica.celeritas.options.OptionControls;
import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen;

/**
 * Makes Video Settings open Reese's Sodium Options. Celeritas replaces vanilla's video settings with its own screen
 * (its {@code MixinGuiOptions}, a cancelling HEAD injection on the button); Demonica swaps that screen for RSO when it
 * is displayed, so both mods keep their own hooks and none is needed on the button. The mod list's Config button opens
 * Celeritas's screen too ({@link com.demonica.gui.DemonicaGuiFactory}) and is swapped the same way.
 *
 * <p>Celeritas's screen stays when RSO's own "enabled" setting is off, when the quarantine patch O1 is not applied
 * (RSO cannot draw sliders and cycling options without it), or when RSO's screen cannot be built. That screen lists
 * RSO's settings page as well ({@link com.demonica.gui.DemonicaOptionPages}), so RSO can be turned back on there.
 */
public final class OptionsScreens {
    private static final Logger LOGGER = LogManager.getLogger("Demonica-OptionHost");

    private boolean loggedUnavailable;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof CeleritasVideoOptionsScreen) {
            // The screen being left is still the current one: Celeritas built its screen with it as the parent.
            GuiScreen replacement = this.rso(Minecraft.getMinecraft().currentScreen);
            if (replacement != null) {
                event.setGui(replacement);
            }
        }
    }

    private @Nullable GuiScreen rso(@Nullable GuiScreen parent) {
        if (!ReeseSodiumOptionsConfig.config().isEnabled()) {
            return null;
        }
        if (!OptionControls.available()) {
            if (!this.loggedUnavailable) {
                this.loggedUnavailable = true;
                LOGGER.warn("Reese's Sodium Options needs the Celeritas patch O1, which is not applied; "
                    + "Video Settings opens Celeritas's own screen");
            }
            return null;
        }
        try {
            return new SodiumVideoOptionsScreen(parent, DemonicaOptionHost.collect());
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("Could not build the Reese's Sodium Options screen; Video Settings opens Celeritas's own screen", e);
            return null;
        }
    }
}
