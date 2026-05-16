package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;

public final class StationTileRenderer {

    private static final int SELECTED_OUTER_BORDER_OFFSET = 1;
    private static final int SELECTED_OUTER_BORDER_SIZE_GROWTH = SELECTED_OUTER_BORDER_OFFSET * 2;

    private StationTileRenderer() {}

    public static void drawHoverOverlay(int x, int y, int size) {
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_BORDER_HOVERED.getColor());
    }

    public static void drawSelectionOverlay(int x, int y, int size) {
        int color = EnumColors.MAP_COLOR_STATION_TILE_BORDER_SELECTED.getColor();
        drawBorder(x, y, size, color);
        drawExpandedBorder(x, y, size, color);
    }

    public static void drawPickerCompatibleOverlay(int x, int y, int size) {
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_PICKER_COMPATIBLE.getColor());
    }

    public static void drawPickerCompatibleSecondaryOverlay(int x, int y, int size) {
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_PICKER_COMPATIBLE_SECONDARY.getColor());
    }

    public static void drawPickerSelectedOverlay(int x, int y, int size) {
        int color = EnumColors.MAP_COLOR_STATION_PICKER_SELECTED.getColor();
        drawBorder(x, y, size, color);
        drawExpandedBorder(x, y, size, color);
    }

    public static void drawPickerSelectedSecondaryOverlay(int x, int y, int size) {
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_PICKER_SELECTED_SECONDARY.getColor());
    }

    public static void drawPickerDeconstructSelectedOverlay(int x, int y, int size) {
        int color = EnumColors.MAP_COLOR_STATION_PICKER_DECONSTRUCT_SELECTED.getColor();
        drawBorder(x, y, size, color);
        drawExpandedBorder(x, y, size, color);
    }

    private static void drawBorder(int x, int y, int size, int color) {
        BorderedRect.drawBorderOnly(x, y, size, size, color);
    }

    private static void drawExpandedBorder(int x, int y, int size, int color) {
        BorderedRect.drawBorderOnly(
            x - SELECTED_OUTER_BORDER_OFFSET,
            y - SELECTED_OUTER_BORDER_OFFSET,
            size + SELECTED_OUTER_BORDER_SIZE_GROWTH,
            size + SELECTED_OUTER_BORDER_SIZE_GROWTH,
            color);
    }
}
