package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;

public class OxygenCollectorGUI {

    /** The scan draws nine cells of nine pixels */
    private static final int SCAN = 81;

    public static ModularPanel build(TileEntityOxygenCollector tile, PosGuiData guiData, PanelSyncManager syncManager) {
        IntSyncValue leaves = new IntSyncValue(() -> tile.cachedLeafCount);
        syncManager.syncValue("leaves", leaves);
        ModularPanel panel = GUIHelper.panel(
            "oxygen_collector",
            "galaxia.gui.oxygen_collector.title",
            tile,
            new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE)),
            new IntSyncValue(() -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE)),
            new IntSyncValue(tile::getStoredOxygen),
            new IntSyncValue(tile::getMaxOxygenBuffer),
            syncManager,
            SCAN);

        // The leaves around the collector, and what it makes of them
        panel.child(
            new LeafScanWidget(tile).pos(8, GUIHelper.CONTENT_Y)
                .size(SCAN, SCAN));
        int textX = SCAN + 10;
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.gui.oxygen_collector.leaves"),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    0.75f)
                .pos(textX, GUIHelper.CONTENT_Y + 2));
        panel.child(
            GalaxiaGuiStyle.label(() -> String.valueOf(leaves.getIntValue()), () -> GalaxiaGuiStyle.TEXT, 1f)
                .pos(textX, GUIHelper.CONTENT_Y + 11));
        panel.child(
            GUIHelper.status(syncManager, "galaxia.gui.common.generating")
                .pos(textX, GUIHelper.CONTENT_Y + 26));
        return panel;
    }
}
