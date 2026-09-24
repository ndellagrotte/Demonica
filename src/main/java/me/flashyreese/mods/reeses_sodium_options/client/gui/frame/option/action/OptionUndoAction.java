package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.RsoOption;
import net.minecraft.util.ResourceLocation;


public final class OptionUndoAction {
    static final ResourceLocation ICON = new ResourceLocation("reeses-sodium-options", "textures/gui/undo_to_unmodified.png");

    public static boolean isVisible(RsoOption option) {
        return ReeseSodiumOptionsConfig.config().isUndoButtonOverlay()
                && (ReeseSodiumOptionsConfig.config().isAlwaysShowActionButtons() || canUndo(option));
    }

    public static boolean isActive(RsoOption option) {
        return ReeseSodiumOptionsConfig.config().isUndoButtonOverlay()
                && canUndo(option);
    }

    // Celeritas's options report a change only while the pending value differs from the applied one.
    public static boolean canUndo(RsoOption option) {
        return option.isEnabled()
                && option.hasChanged();
    }

    public static void undoChanges(RsoOption option) {
        option.undo();
    }
}
