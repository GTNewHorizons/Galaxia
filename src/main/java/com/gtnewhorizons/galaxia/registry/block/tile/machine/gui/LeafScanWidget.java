package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.block.Block;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;

public class LeafScanWidget extends Widget<LeafScanWidget> {

    private final TileEntityOxygenCollector tile;
    private static final UITexture GRID_TEX = EnumTextures.MIRAGE.getImage();
    private static final UITexture DOT_TEX = EnumTextures.TENEBRAE.getImage();
    private final int size = 81;

    public LeafScanWidget(TileEntityOxygenCollector tile) {
        this.tile = tile;
        size(size, size);
        background(EnumTextures.SELECTION_FRAME.getImage());
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        GRID_TEX.draw(context, 0, 0, size, size, widgetTheme.getTheme());

        int radius = 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (hasLeafAt(dx, dz)) {
                    int px = (dx + radius) * 9;
                    int py = (dz + radius) * 9;
                    DOT_TEX.withColorOverride(EnumColors.MAP_LEAF_GREEN.getColor())
                        .draw(context, px, py, 9, 9, widgetTheme.getTheme());
                }
            }
        }
        DOT_TEX.withColorOverride(EnumColors.MAP_MACHINE_BLUE.getColor())
            .draw(context, 36, 36, 9, 9, widgetTheme.getTheme());
    }

    private boolean hasLeafAt(int dx, int dz) {
        if (tile.getWorldObj() == null) return false;
        int worldX = tile.xCoord + dx;
        int worldZ = tile.zCoord + dz;
        for (int y = tile.yCoord - 3; y <= tile.yCoord + 3; y++) {
            Block b = tile.getWorldObj()
                .getBlock(worldX, y, worldZ);
            if (b != null && b.isLeaves(tile.getWorldObj(), worldX, y, worldZ)) return true;
        }
        return false;
    }
}
