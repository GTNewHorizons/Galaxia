package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.gui.Gui;

public class BorderedRect {

    public static void draw(int x, int y, int w, int h, int backgroundColor, int borderColor) {
        Gui.drawRect(x, y, x + w, y + h, backgroundColor);
        drawBorderOnly(x, y, w, h, borderColor);
    }

    public static void drawBorderOnly(int x, int y, int w, int h, int borderColor) {
        Gui.drawRect(x, y, x + w, y + 1, borderColor);
        Gui.drawRect(x, y + h - 1, x + w, y + h, borderColor);
        Gui.drawRect(x, y, x + 1, y + h, borderColor);
        Gui.drawRect(x + w - 1, y, x + w, y + h, borderColor);
    }
}
