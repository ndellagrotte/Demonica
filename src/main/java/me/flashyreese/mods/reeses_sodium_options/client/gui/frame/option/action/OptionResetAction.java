package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoOption;
import net.minecraft.util.ResourceLocation;


public final class OptionResetAction {
    static final ResourceLocation ICON = new ResourceLocation("reeses-sodium-options", "textures/gui/reset_to_default.png");

    public static boolean isVisible(RsoOption option) {
        return ReeseSodiumOptionsConfig.config().isResetButtonOverlay()
                && (ReeseSodiumOptionsConfig.config().isAlwaysShowActionButtons() || canReset(option));
    }

    public static boolean isActive(RsoOption option) {
        return ReeseSodiumOptionsConfig.config().isResetButtonOverlay()
                && canReset(option);
    }

    public static boolean canReset(RsoOption option) {
        return option.isEnabled()
                && option.differsFromDefault();
    }

    public static void resetToDefault(RsoOption option) {
        option.resetToDefault();
    }
}
