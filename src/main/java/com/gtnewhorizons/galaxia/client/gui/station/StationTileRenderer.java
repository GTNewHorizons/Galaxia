package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

public final class StationTileRenderer {

    public static final int LOGICAL_TILE_SIZE = 24;

    private StationTileRenderer() {}

    public static void drawOccupied(GuiContext ctx, int x, int y, int size, PlacedTile tile) {
        int fillColor = categoryColor(categoryOf(tile));
        Gui.drawRect(x, y, x + size, y + size, fillColor);
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_BORDER_DEFAULT.getColor());
        drawLabel(ctx, x, y, size, labelOf(tile));

        switch (tile.state()) {
            case UNDER_CONSTRUCTION -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_UNDER_CONSTRUCTION.getColor());
            case UNDER_DECONSTRUCTION -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_UNDER_DECONSTRUCTION.getColor());
            case OCCUPIED_DISABLED -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_DISABLED_DIM.getColor());
            case BLOCKED -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_BLOCKED.getColor());
            case OCCUPIED_OPERATIONAL, EMPTY -> {}
        }
    }

    public static void drawEmptyExpansionSlot(GuiContext ctx, int x, int y, int size) {
        Gui.drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_EMPTY_FILL.getColor());
        drawDashedBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_EMPTY_BORDER.getColor());
    }

    public static void drawHoverOverlay(int x, int y, int size) {
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_BORDER_HOVERED.getColor());
    }

    public static void drawSelectionOverlay(int x, int y, int size) {
        int color = EnumColors.MAP_COLOR_STATION_TILE_BORDER_SELECTED.getColor();
        drawBorder(x, y, size, color);
        drawBorder(x - 1, y - 1, size + 2, color);
    }

    private static StationModuleCategory categoryOf(PlacedTile tile) {
        if (tile == null) return StationModuleCategory.COMMAND;
        ModuleInstance module = tile.module();
        if (module == null) return StationModuleCategory.COMMAND;
        FacilityModuleKind kind = module.kind();
        return kind == null ? StationModuleCategory.COMMAND : kind.getCategory();
    }

    private static int categoryColor(StationModuleCategory category) {
        return switch (category) {
            case COMMAND -> EnumColors.MAP_COLOR_STATION_CATEGORY_COMMAND.getColor();
            case MINING_SUPPORT -> EnumColors.MAP_COLOR_STATION_CATEGORY_MINING_SUPPORT.getColor();
            case LOGISTICS -> EnumColors.MAP_COLOR_STATION_CATEGORY_LOGISTICS.getColor();
            case STORAGE -> EnumColors.MAP_COLOR_STATION_CATEGORY_STORAGE.getColor();
            case POWER -> EnumColors.MAP_COLOR_STATION_CATEGORY_POWER.getColor();
            case PROCESSING -> EnumColors.MAP_COLOR_STATION_CATEGORY_PROCESSING.getColor();
            case HABITATION -> EnumColors.MAP_COLOR_STATION_CATEGORY_HABITATION.getColor();
        };
    }

    private static String labelOf(PlacedTile tile) {
        if (tile == null) return "";
        ModuleInstance module = tile.module();
        if (module == null) return "C";
        FacilityModuleKind kind = module.kind();
        return kind == null ? "?"
            : kind.name()
                .substring(0, 1);
    }

    private static void drawLabel(GuiContext ctx, int x, int y, int size, String label) {
        if (label.isEmpty()) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int textWidth = fr.getStringWidth(label);
        int textX = x + (size - textWidth) / 2;
        int textY = y + (size - fr.FONT_HEIGHT) / 2 + 1;
        fr.drawStringWithShadow(label, textX, textY, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    private static void drawBorder(int x, int y, int size, int color) {
        BorderedRect.drawBorderOnly(x, y, size, size, color);
    }

    private static void drawDashedBorder(int x, int y, int size, int color) {
        int step = 3;
        int dash = 2;
        for (int i = 0; i < size; i += step) {
            int end = Math.min(i + dash, size);
            Gui.drawRect(x + i, y, x + end, y + 1, color);
            Gui.drawRect(x + i, y + size - 1, x + end, y + size, color);
            Gui.drawRect(x, y + i, x + 1, y + end, color);
            Gui.drawRect(x + size - 1, y + i, x + size, y + end, color);
        }
    }

    public static StationTileState derivedState(PlacedTile tile) {
        return tile == null ? StationTileState.EMPTY : tile.state();
    }
}
