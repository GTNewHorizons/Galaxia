package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenFiller;

public class OxygenFillerGUI {

    public static ModularPanel build(TileEntityOxygenFiller tile, PosGuiData guiData, PanelSyncManager syncManager) {
        ModularPanel panel = GUIHelper.panel(
            "oxygen_filler",
            "galaxia.gui.oxygen_filler.title",
            tile,
            new IntSyncValue(() -> (int) Math.min(tile.storedEnergy, Integer.MAX_VALUE)),
            new IntSyncValue(() -> (int) Math.min(tile.getMaxEnergyBuffer(), Integer.MAX_VALUE)),
            new IntSyncValue(tile::getStoredOxygen),
            new IntSyncValue(tile::getMaxOxygenBuffer),
            syncManager,
            18);

        // Tank to fill, and where it comes out full
        IItemHandler items = tile.getItemHandler();
        panel.child(
            new ItemSlot().slot(new ModularSlot(items, 0))
                .background(EnumTextures.GUI_SLOT.getImage())
                .pos(8, GUIHelper.CONTENT_Y));
        panel.child(
            IKey.str(">")
                .color(GalaxiaGuiStyle.TEXT)
                .asWidget()
                .pos(31, GUIHelper.CONTENT_Y + 5));
        panel.child(
            new ItemSlot().slot(new ModularSlot(items, 1))
                .background(EnumTextures.GUI_SLOT.getImage())
                .pos(42, GUIHelper.CONTENT_Y));
        panel.child(
            GUIHelper.status(syncManager, "galaxia.gui.common.filling")
                .pos(68, GUIHelper.CONTENT_Y + 5));
        return panel;
    }
}
