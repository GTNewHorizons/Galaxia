package com.gtnewhorizons.galaxia.client.gui.station.layer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;

public final class ModuleLayerRenderer {

    private static final int BORDER_DEFAULT = 0xFF2D435D;
    private static final int EMPTY_SLOT_FILL = 0x3318273A;
    private static final int EMPTY_SLOT_BORDER = 0xFF556577;
    private static final int UNDER_CONSTRUCTION_OVERLAY = 0x66FFD59A;
    private static final int UNDER_DECONSTRUCTION_OVERLAY = 0x66FF5A5A;
    private static final int DISABLED_DIM_OVERLAY = 0x80000000;
    private static final int BLOCKED_OVERLAY = 0x66FF0000;

    private ModuleLayerRenderer() {}

    public static void drawOccupied(GuiContext ctx, int x, int y, PlacedTile tile) {
        int size = StationMapViewport.TILE_SIZE;
        int fillColor = categoryColor(categoryOf(tile));
        Gui.drawRect(x, y, x + size, y + size, fillColor);
        drawLabel(ctx, x, y, size, labelOf(tile));
        drawBorder(x, y, size, BORDER_DEFAULT);

        switch (tile.state()) {
            case UNDER_CONSTRUCTION -> Gui.drawRect(x, y, x + size, y + size, UNDER_CONSTRUCTION_OVERLAY);
            case UNDER_DECONSTRUCTION -> Gui.drawRect(x, y, x + size, y + size, UNDER_DECONSTRUCTION_OVERLAY);
            case OCCUPIED_DISABLED -> Gui.drawRect(x, y, x + size, y + size, DISABLED_DIM_OVERLAY);
            case BLOCKED -> Gui.drawRect(x, y, x + size, y + size, BLOCKED_OVERLAY);
            case OCCUPIED_OPERATIONAL, EMPTY -> {}
        }
    }

    public static void drawExpansionSlot(GuiContext ctx, int x, int y) {
        int size = StationMapViewport.TILE_SIZE;
        Gui.drawRect(x, y, x + size, y + size, EMPTY_SLOT_FILL);
        drawDashedBorder(x, y, size, EMPTY_SLOT_BORDER);
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
            case COMMAND -> 0xFF3A5678;
            case MINING_SUPPORT -> 0xFF6B5020;
            case LOGISTICS -> 0xFF2D6A7A;
            case STORAGE -> 0xFF4A4A4A;
            case POWER -> 0xFF2F5A3A;
            case PROCESSING -> 0xFF4A2B66;
            case HABITATION -> 0xFF5A4030;
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
        Gui.drawRect(x, y, x + size, y + 1, color);
        Gui.drawRect(x, y + size - 1, x + size, y + size, color);
        Gui.drawRect(x, y, x + 1, y + size, color);
        Gui.drawRect(x + size - 1, y, x + size, y + size, color);
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
}
