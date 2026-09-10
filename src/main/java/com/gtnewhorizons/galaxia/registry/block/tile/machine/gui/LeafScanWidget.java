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
    private static final int RADIUS = 4;
    private static final int DIAMETER = RADIUS * 2 + 1;
    private static final int CELL_SIZE = 9;
    private static final int SIZE = DIAMETER * CELL_SIZE;
    private final boolean[] leaves = new boolean[DIAMETER * DIAMETER];

    public LeafScanWidget(TileEntityOxygenCollector tile) {
        this.tile = tile;
        size(SIZE, SIZE);
        background(EnumTextures.SELECTION_FRAME.getImage());
    }

    @Override
    public void onInit() {
        super.onInit();
        refreshLeaves();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        refreshLeaves();
    }

    private void refreshLeaves() {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                leaves[(dx + RADIUS) * DIAMETER + dz + RADIUS] = hasLeafAt(dx, dz);
            }
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        GRID_TEX.draw(context, 0, 0, SIZE, SIZE, widgetTheme.getTheme());

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (leaves[(dx + RADIUS) * DIAMETER + dz + RADIUS]) {
                    int px = (dx + RADIUS) * CELL_SIZE;
                    int py = (dz + RADIUS) * CELL_SIZE;
                    DOT_TEX.withColorOverride(EnumColors.MAP_LEAF_GREEN.getColor())
                        .draw(context, px, py, CELL_SIZE, CELL_SIZE, widgetTheme.getTheme());
                }
            }
        }
        DOT_TEX.withColorOverride(EnumColors.MAP_MACHINE_BLUE.getColor())
            .draw(context, RADIUS * CELL_SIZE, RADIUS * CELL_SIZE, CELL_SIZE, CELL_SIZE, widgetTheme.getTheme());
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
