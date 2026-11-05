package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.gui.Gui;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;

/**
 * Frame drawn by hand-made widgets of the map and the station screens. Panels, buttons and wells in the shared GUI
 * colours are drawn with the shared textures, so these widgets look like the rest of the interface; other colours
 * stay a flat rectangle with a border
 */
public class BorderedRect {

    public static void draw(int x, int y, int w, int h, int backgroundColor, int borderColor) {
        EnumTextures texture = themed(backgroundColor);
        if (texture != null) {
            texture.getImage()
                .draw(x, y, w, h);
            return;
        }
        Gui.drawRect(x, y, x + w, y + h, backgroundColor);
        drawBorderOnly(x, y, w, h, borderColor);
    }

    private static EnumTextures themed(int backgroundColor) {
        if (backgroundColor == EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor()
            || backgroundColor == EnumColors.MAP_COLOR_MODAL_BG.getColor()) return EnumTextures.GUI_PANEL;
        if (backgroundColor == EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor()) return EnumTextures.GUI_BUTTON;
        if (backgroundColor == EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()) {
            return EnumTextures.GUI_BUTTON_HOVERED;
        }
        if (backgroundColor == EnumColors.MAP_COLOR_SCROLL_BG.getColor()
            || backgroundColor == EnumColors.MAP_COLOR_RENAME_INPUT_BG.getColor()) return EnumTextures.GUI_WELL;
        return null;
    }

    public static void drawBorderOnly(int x, int y, int w, int h, int borderColor) {
        Gui.drawRect(x, y, x + w, y + 1, borderColor);
        Gui.drawRect(x, y + h - 1, x + w, y + h, borderColor);
        Gui.drawRect(x, y, x + 1, y + h, borderColor);
        Gui.drawRect(x + w - 1, y, x + w, y + h, borderColor);
    }
}
