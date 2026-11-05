package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

public class OxygenPylonGUI {

    /** The radar draws nine cells of nine pixels */
    private static final int RADAR = 81;

    public static ModularPanel build(TileEntityOxygenPylon tile, PosGuiData guiData, PanelSyncManager syncManager) {
        IntSyncValue charged = new IntSyncValue(() -> tile.lastChargedCount);
        syncManager.syncValue("charged", charged);
        ModularPanel panel = GUIHelper.panel(
            "oxygen_pylon",
            "galaxia.gui.oxygen_pylon.title",
            tile,
            new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE)),
            new IntSyncValue(() -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE)),
            new IntSyncValue(tile::getStoredOxygen),
            new IntSyncValue(tile::getMaxOxygenBuffer),
            syncManager,
            RADAR);

        // The players around the pylon it keeps breathing
        panel.child(
            new PlayerRadarWidget(tile).pos(8, GUIHelper.CONTENT_Y)
                .size(RADAR, RADAR));
        int textX = RADAR + 10;
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.oxygen_pylon.radius",
                        TileEntityOxygenPylon.PYLON_RADIUS),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    1f)
                .pos(textX, GUIHelper.CONTENT_Y + 2));
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.gui.oxygen_pylon.players"),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    0.75f)
                .pos(textX, GUIHelper.CONTENT_Y + 16));
        panel.child(
            GalaxiaGuiStyle.label(() -> String.valueOf(charged.getIntValue()), () -> GalaxiaGuiStyle.TEXT, 1f)
                .pos(textX, GUIHelper.CONTENT_Y + 25));
        panel.child(
            GUIHelper.status(syncManager, "galaxia.gui.common.active")
                .pos(textX, GUIHelper.CONTENT_Y + 40));
        return panel;
    }
}
