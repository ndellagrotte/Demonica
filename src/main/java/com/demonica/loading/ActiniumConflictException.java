package com.demonica.loading;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraftforge.fml.client.CustomModLoadingErrorDisplayException;
import net.minecraftforge.fml.common.EnhancedRuntimeException;

/** Stops loading with a readable screen when Actinium is installed next to Demonica. */
public final class ActiniumConflictException extends CustomModLoadingErrorDisplayException {
    private static final String[] LINES = {
        "Demonica and Actinium are both installed.",
        "",
        "Demonica replaces Actinium: it is the same shader mod, running on",
        "the separately installed Celeritas mod instead of a copy built into it.",
        "The two patch the same parts of the game and cannot run together.",
        "",
        "Remove one of them from the mods folder.",
        "To keep using Actinium, remove Demonica (and Celeritas, if nothing else needs it)."
    };

    public ActiniumConflictException() {
        super("Demonica cannot run together with Actinium; remove one of them", null);
    }

    @Override
    public void initGui(GuiErrorScreen errorScreen, FontRenderer fontRenderer) {
    }

    @Override
    public void drawScreen(GuiErrorScreen errorScreen, FontRenderer fontRenderer, int mouseRelX, int mouseRelY, float tickTime) {
        int y = errorScreen.height / 2 - LINES.length * 6;
        for (String line : LINES) {
            errorScreen.drawCenteredString(fontRenderer, line, errorScreen.width / 2, y, 0xFFFFFF);
            y += 12;
        }
    }

    @Override
    public void printStackTrace(EnhancedRuntimeException.WrappedPrintStream stream) {
        for (String line : LINES) {
            stream.println(line);
        }
    }
}
