package net.coderbot.iris.gui.screen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Shown over the shader pack screen when the pack requires a feature flag this install does not support
 * (ShaderPack's required-feature check). Back returns to the pack screen, which already has shaders switched off.
 */
// Demonica: modeled on ShadersUnavailableScreen (legacy GuiScreen, no MultiLineLabel / Component); upstream's
// iris/gui/FeatureMissingErrorScreen shows the same title, wrapped message and a Back button.
public class FeatureMissingErrorScreen extends GuiScreen {
    private static final int BACK = 0;

    private final @Nullable GuiScreen parent;
    private final String title;
    private final String message;

    public FeatureMissingErrorScreen(@Nullable GuiScreen parent, String title, String message) {
        this.parent = parent;
        this.title = title;
        this.message = message;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        // Demonica: anchored to the bottom instead of upstream's fixed y=140, which a message wrapped to several
        // lines from y=110 (more on small GUI scales) would run into.
        this.buttonList.add(new GuiButton(BACK, this.width / 2 - 100, this.height - 40, I18n.format("gui.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) {
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
        this.drawCenteredString(this.fontRenderer, this.title, center, 90, 0xFFFFFF);
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(this.message, this.width - 50);
        int y = 110;
        for (String line : lines) {
            this.drawCenteredString(this.fontRenderer, line, center, y, 0xE0E0E0);
            y += this.fontRenderer.FONT_HEIGHT + 2;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
