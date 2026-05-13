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
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

public class OxygenPylonGUI {

    public static ModularPanel build(TileEntityOxygenPylon tile, PosGuiData guiData, PanelSyncManager syncManager) {
        IntSyncValue energySync = new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE), v -> {});
        IntSyncValue maxEnergySync = new IntSyncValue(
            () -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE),
            v -> {});
        IntSyncValue oxygenSync = new IntSyncValue(tile::getStoredOxygen, v -> {});
        IntSyncValue maxOxygenSync = new IntSyncValue(tile::getMaxOxygenBuffer, v -> {});
        IntSyncValue chargedSync = new IntSyncValue(() -> tile.lastChargedCount, v -> {});

        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("maxEnergy", maxEnergySync);
        syncManager.syncValue("oxygen", oxygenSync);
        syncManager.syncValue("maxOxygen", maxOxygenSync);
        syncManager.syncValue("charged", chargedSync);

        return ModularPanel.defaultPanel("oxygen_pylon", 176, 200)
            .child(
                IKey.lang("galaxia.gui.oxygen_pylon.title")
                    .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .asWidget()
                    .top(6)
                    .left(8)
                    .horizontalCenter())

            .child(GUIHelper.createEnergyBar(energySync, maxEnergySync, 8, 22))
            .child(GUIHelper.createOxygenBar(oxygenSync, maxOxygenSync, 8, 48))

            // Player Radar
            .child(
                new PlayerRadarWidget(tile).pos(8, 72)
                    .size(81, 81))

            .child(
                Flow.column()
                    .top(162)
                    .left(8)
                    .right(8)
                    .child(
                        IKey.lang("galaxia.gui.oxygen_pylon.radius", TileEntityOxygenPylon.PYLON_RADIUS)
                            .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                            .asWidget()
                            .height(12))
                    .child(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocalFormatted(
                                "galaxia.gui.oxygen_pylon.players_charged",
                                chargedSync.getIntValue()))
                            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .asWidget()
                            .height(12))
                    .child(
                        IKey.dynamic(
                            () -> tile.active
                                ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("galaxia.gui.common.active")
                                : EnumChatFormatting.GRAY + StatCollector.translateToLocal("galaxia.gui.common.idle"))
                            .asWidget()
                            .height(12)));
    }
}
