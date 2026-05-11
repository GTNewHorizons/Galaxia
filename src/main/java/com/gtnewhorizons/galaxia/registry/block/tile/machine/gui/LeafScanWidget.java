package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;

public class LeafScanWidget extends Widget<LeafScanWidget> {

    private static final int GRID_SIZE = 9;
    private static final int CELL_SIZE = 9;
    private static final int SCAN_RADIUS = 4;

    private final TileEntityOxygenCollector tile;

    public LeafScanWidget(TileEntityOxygenCollector tile) {
        this.tile = tile;

        background(EnumTextures.SELECTION_FRAME.getImage());
        tooltip(t -> t.addLine(IKey.lang("galaxia.gui.oxygen_filler.leaf_scan")));
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        if (tile.getWorldObj() == null) {
            return;
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
            for (int gridY = 0; gridY < GRID_SIZE; gridY++) {

                int worldX = tile.xCoord + (gridX - SCAN_RADIUS);
                int worldZ = tile.zCoord + (gridY - SCAN_RADIUS);

                boolean foundLeaf = false;

                for (int y = tile.yCoord - 2; y <= tile.yCoord + 2; y++) {
                    Block block = tile.getWorldObj().getBlock(worldX, y, worldZ);

                    if (block == Blocks.leaves || block == Blocks.leaves2) {
                        foundLeaf = true;
                        break;
                    }

                    if (block != null && block.isLeaves(tile.getWorldObj(), worldX, y, worldZ)) {
                        foundLeaf = true;
                        break;
                    }
                }

                int px = gridX * CELL_SIZE;
                int py = gridY * CELL_SIZE;

                if (foundLeaf) {
                    GL11.glColor4f(0.2f, 1.0f, 0.3f, 1.0f);
                } else {
                    GL11.glColor4f(0.25f, 0.25f, 0.25f, 1.0f);
                }

                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(px + 1, py + 1);
                GL11.glVertex2f(px + CELL_SIZE - 1, py + 1);
                GL11.glVertex2f(px + CELL_SIZE - 1, py + CELL_SIZE - 1);
                GL11.glVertex2f(px + 1, py + CELL_SIZE - 1);
                GL11.glEnd();
            }
        }

        int centerX = (GRID_SIZE / 2) * CELL_SIZE;
        int centerY = (GRID_SIZE / 2) * CELL_SIZE;

        GL11.glColor4f(0.3f, 0.9f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(centerX + 2, centerY + 2);
        GL11.glVertex2f(centerX + CELL_SIZE - 2, centerY + 2);
        GL11.glVertex2f(centerX + CELL_SIZE - 2, centerY + CELL_SIZE - 2);
        GL11.glVertex2f(centerX + 2, centerY + CELL_SIZE - 2);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
