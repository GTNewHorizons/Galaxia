package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
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
        IntSyncValue energySync = new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE), v -> {});
        IntSyncValue maxEnergySync = new IntSyncValue(
            () -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE),
            v -> {});
        IntSyncValue oxygenSync = new IntSyncValue(tile::getStoredOxygen, v -> {});
        IntSyncValue maxOxygenSync = new IntSyncValue(tile::getMaxOxygenBuffer, v -> {});

        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("maxEnergy", maxEnergySync);
        syncManager.syncValue("oxygen", oxygenSync);
        syncManager.syncValue("maxOxygen", maxOxygenSync);

        IItemHandler handler = tile.getItemHandler();

        return ModularPanel.defaultPanel("oxygen_filler", 176, 240)
            .bindPlayerInventory()
            .child(
                IKey.lang("galaxia.gui.oxygen_filler.title")
                    .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .asWidget()
                    .top(6)
                    .left(8))

            // Bars
            .child(GUIHelper.createEnergyBar(energySync, maxEnergySync, 8, 22))
            .child(GUIHelper.createOxygenBar(oxygenSync, maxOxygenSync, 8, 48))

            // Machine slots 3x2
            .child(createMachineSlots(handler, 8, 160))

            // Status
            .child(
                IKey.dynamic(
                    () -> tile.active
                        ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("galaxia.gui.common.filling")
                        : EnumChatFormatting.GRAY + StatCollector.translateToLocal("galaxia.gui.common.idle"))
                    .asWidget()
                    .top(222)
                    .left(8));
    }

    private static Flow createMachineSlots(IItemHandler handler, int x, int y) {
        Flow row1 = Flow.row()
            .top(y)
            .left(x);
        Flow row2 = Flow.row()
            .top(y + 20)
            .left(x);

        for (int i = 0; i < 3; i++) {
            row1.child(
                new ItemSlot().slot(new ModularSlot(handler, i))
                    .marginRight(2));
        }
        for (int i = 3; i < 6; i++) {
            row2.child(
                new ItemSlot().slot(new ModularSlot(handler, i))
                    .marginRight(2));
        }
        return Flow.column()
            .child(row1)
            .child(row2);
    }
}
