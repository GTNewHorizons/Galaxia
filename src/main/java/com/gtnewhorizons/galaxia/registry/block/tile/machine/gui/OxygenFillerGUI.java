package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenFiller;

public class OxygenFillerGUI {

    public static ModularPanel build(TileEntityOxygenFiller tile, PosGuiData guiData, PanelSyncManager syncManager) {
        IntSyncValue energySync = new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE), _ -> {});
        IntSyncValue maxEnergySync = new IntSyncValue(
            () -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE),
            _ -> {});
        IntSyncValue oxygenSync = new IntSyncValue(tile::getStoredOxygen, _ -> {});
        IntSyncValue maxOxygenSync = new IntSyncValue(tile::getMaxOxygenBuffer, _ -> {});

        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("maxEnergy", maxEnergySync);
        syncManager.syncValue("oxygen", oxygenSync);
        syncManager.syncValue("maxOxygen", maxOxygenSync);

        return ModularPanel.defaultPanel("oxygen_filler", 176, 220)
            .bindPlayerInventory()
            .child(
                IKey.lang("galaxia.gui.oxygen_filler.title")
                    .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .asWidget()
                    .top(6)
                    .left(8)
                    .horizontalCenter())
            .child(GUIHelper.createEnergyBar(energySync, maxEnergySync, 8, 20))
            .child(GUIHelper.createOxygenBar(oxygenSync, maxOxygenSync, 8, 35))
            .child(createMachineSlots(tile.getItemHandler(), 0, 0).center())
            .child(
                IKey.dynamic(
                    () -> tile.active
                        ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("galaxia.gui.common.filling")
                        : EnumChatFormatting.GRAY + StatCollector.translateToLocal("galaxia.gui.common.idle"))
                    .asWidget()
                    .bottom(90)
                    .left(8));
    }

    private static Flow createMachineSlots(IItemHandler handler, int x, int y) {
        return Flow.row()
            .top(y)
            .left(x)
            .childPadding(6)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(new ItemSlot().slot(new ModularSlot(handler, 0)))
            .child(new ItemSlot().slot(new ModularSlot(handler, 1)))
            .coverChildren();
    }
}
