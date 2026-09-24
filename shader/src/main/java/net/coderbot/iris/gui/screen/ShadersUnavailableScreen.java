package net.coderbot.iris.gui.screen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Shown in place of the shader pack screen when the host has turned the shader pipeline off (IrisDebugOptions), with
 * its reason: packs cannot be loaded or configured in this game.
 */
public class ShadersUnavailableScreen extends GuiScreen {
    private static final int DONE = 0;

    private final @Nullable GuiScreen parent;
    private final String reason;

    public ShadersUnavailableScreen(@Nullable GuiScreen parent, String reason) {
        this.parent = parent;
        this.reason = reason;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(DONE, this.width / 2 - 100, this.height - 40, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int center = this.width / 2;
        this.drawCenteredString(this.fontRenderer, I18n.format("options.iris.shaderPackSelection.title"), center, 15, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, I18n.format("options.iris.shaderPackSelection.unavailable"), center, 40, 0xFF5555);
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(this.reason, Math.min(this.width - 40, 400));
        int y = 60;
        for (String line : lines) {
            this.drawCenteredString(this.fontRenderer, line, center, y, 0xE0E0E0);
            y += this.fontRenderer.FONT_HEIGHT + 2;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
