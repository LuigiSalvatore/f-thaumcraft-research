package com.luigi.thaumcraft.researchsolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/** Texture-free Thaumcraft-toned palette control with a compatibility fallback. */
final class PaletteButton extends GuiButton {
    PaletteButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (Config.plainButtonMode) {
            super.drawButton(minecraft, mouseX, mouseY);
            return;
        }
        if (!visible) return;
        boolean hover = mouseX >= xPosition && mouseY >= yPosition &&
            mouseX < xPosition + width && mouseY < yPosition + height;
        int border = enabled ? (hover ? 0xffd8b85a : 0xff8c7137) : 0xff4c4639;
        int fill = enabled ? (hover ? 0xe02e2921 : 0xd01d1a17) : 0xb0181818;
        Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, border);
        Gui.drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, fill);
        int color = enabled ? (hover ? 0xffffe7a0 : 0xffe6d8b4) : 0xff77736a;
        drawCenteredString(minecraft.fontRenderer, displayString,
            xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
