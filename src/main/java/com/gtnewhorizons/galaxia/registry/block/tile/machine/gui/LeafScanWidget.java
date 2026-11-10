package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.block.Block;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;

/**
 * The leaves around an oxygen collector, seen from above
 */
public class LeafScanWidget extends Widget<LeafScanWidget> {

    /** Oak leaves are grey until tinted, like the foliage colour tints them in the world */
    private static final UITexture LEAVES = UITexture.fullImage("minecraft", "textures/blocks/leaves_oak_opaque.png");
    private static final int LEAF_TINT = 0xFF4E9A2E;

    private final TileEntityOxygenCollector tile;
    private final boolean[] leaves = new boolean[RadarGrid.CELLS * RadarGrid.CELLS];

    public LeafScanWidget(TileEntityOxygenCollector tile) {
        this.tile = tile;
        size(RadarGrid.SIZE, RadarGrid.SIZE);
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
        for (int dx = -RadarGrid.RADIUS; dx <= RadarGrid.RADIUS; dx++) {
            for (int dz = -RadarGrid.RADIUS; dz <= RadarGrid.RADIUS; dz++) {
                leaves[(dx + RadarGrid.RADIUS) * RadarGrid.CELLS + dz + RadarGrid.RADIUS] = hasLeafAt(dx, dz);
            }
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);
        RadarGrid.draw(context, widgetTheme, EnumTextures.ICON_OXYGEN_COLLECTOR.getImage());
        UITexture leaf = LEAVES.withColorOverride(LEAF_TINT);
        for (int dx = -RadarGrid.RADIUS; dx <= RadarGrid.RADIUS; dx++) {
            for (int dz = -RadarGrid.RADIUS; dz <= RadarGrid.RADIUS; dz++) {
                if (!leaves[(dx + RadarGrid.RADIUS) * RadarGrid.CELLS + dz + RadarGrid.RADIUS]) continue;
                leaf.draw(
                    context,
                    (dx + RadarGrid.RADIUS) * RadarGrid.CELL,
                    (dz + RadarGrid.RADIUS) * RadarGrid.CELL,
                    RadarGrid.CELL,
                    RadarGrid.CELL,
                    widgetTheme.getTheme());
            }
        }
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
