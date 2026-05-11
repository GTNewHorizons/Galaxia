package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;

public class OxygenCollectorGUI {

    public static ModularPanel build(TileEntityOxygenCollector tile, PosGuiData guiData, PanelSyncManager syncManager) {
        IntSyncValue energySync = new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE), v -> {});
        IntSyncValue maxEnergySync = new IntSyncValue(
            () -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE),
            v -> {});
        IntSyncValue oxygenSync = new IntSyncValue(tile::getStoredOxygen, v -> {});
        IntSyncValue maxOxygenSync = new IntSyncValue(tile::getMaxOxygenBuffer, v -> {});
        IntSyncValue leavesSync = new IntSyncValue(() -> tile.cachedLeafCount, v -> {});

        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("maxEnergy", maxEnergySync);
        syncManager.syncValue("oxygen", oxygenSync);
        syncManager.syncValue("maxOxygen", maxOxygenSync);
        syncManager.syncValue("leaves", leavesSync);

        return ModularPanel.defaultPanel("oxygen_collector", 176, 220)
            .child(
                IKey.lang("galaxia.gui.oxygen_collector.title")
                    .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .asWidget()
                    .top(6)
                    .left(8)
                    .horizontalCenter())

            // Energy Bar
            .child(GUIHelper.createEnergyBar(energySync, maxEnergySync, 8, 22))

            // Oxygen Bar
            .child(GUIHelper.createOxygenBar(oxygenSync, maxOxygenSync, 8, 48))

            .child(
                new LeafScanWidget(tile).pos(8, 72)
                    .size(81, 81))

            // Info
            .child(
                Flow.column()
                    .top(158)
                    .left(8)
                    .coverChildrenHeight()
                    .child(
                        IKey.lang("galaxia.gui.oxygen_collector.leaves_in_range", leavesSync.getIntValue())
                            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .asWidget()
                            .height(12))
                    .child(
                        IKey.dynamic(
                            () -> tile.active
                                ? EnumChatFormatting.GREEN
                                    + StatCollector.translateToLocal("galaxia.gui.common.generating")
                                : EnumChatFormatting.GRAY + StatCollector.translateToLocal("galaxia.gui.common.idle"))
                            .asWidget()
                            .height(12)));
    }
}
