package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;

public final class StationTileRenderer {

    private StationTileRenderer() {}

    public static void drawHoverOverlay(int x, int y, int size) {
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_BORDER_HOVERED.getColor());
    }

    public static void drawSelectionOverlay(int x, int y, int size) {
        int color = EnumColors.MAP_COLOR_STATION_TILE_BORDER_SELECTED.getColor();
        drawBorder(x, y, size, color);
        drawBorder(x - 1, y - 1, size + 2, color);
    }

    private static void drawBorder(int x, int y, int size, int color) {
        BorderedRect.drawBorderOnly(x, y, size, size, color);
    }
}
