package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.gtnewhorizons.galaxia.client.EnumTextures;

/**
 * The grid the oxygen machines map their surroundings on: nine by nine cells, one per block, with the machine in the
 * middle
 */
final class RadarGrid {

    static final int RADIUS = 4;
    static final int CELLS = RADIUS * 2 + 1;
    static final int CELL = 9;
    static final int SIZE = CELLS * CELL;

    private RadarGrid() {}

    /** Draws the empty grid with the machine's own block in the middle */
    static void draw(ModularGuiContext context, WidgetThemeEntry<?> theme, UITexture machine) {
        UITexture cell = EnumTextures.GUI_RADAR_CELL.getImage();
        for (int x = 0; x < CELLS; x++) {
            for (int y = 0; y < CELLS; y++) {
                cell.draw(context, x * CELL, y * CELL, CELL, CELL, theme.getTheme());
            }
        }
        machine.draw(context, RADIUS * CELL, RADIUS * CELL, CELL, CELL, theme.getTheme());
    }
}
