package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityGalaxiaMachine;

/**
 * Shared layout of the oxygen machine screens: title and working lamp, energy and oxygen meters, the machine's own
 * part and the player inventory
 */
final class GUIHelper {

    static final int WIDTH = 176;
    /** Top of the machine's own part of the screen */
    static final int CONTENT_Y = 62;

    private GUIHelper() {}

    /**
     * The screen of an oxygen machine, its own part left to fill in
     *
     * @param contentHeight Height of the machine's own part, the inventory goes below it
     */
    static ModularPanel panel(String name, String titleKey, TileEntityGalaxiaMachine tile, IntSyncValue energy,
        IntSyncValue maxEnergy, IntSyncValue oxygen, IntSyncValue maxOxygen, PanelSyncManager syncManager,
        int contentHeight) {
        BooleanSyncValue active = new BooleanSyncValue(() -> tile.active);
        syncManager.syncValue("active", active);
        syncManager.syncValue("energy", energy);
        syncManager.syncValue("maxEnergy", maxEnergy);
        syncManager.syncValue("oxygen", oxygen);
        syncManager.syncValue("maxOxygen", maxOxygen);

        int inventoryY = CONTENT_Y + contentHeight + 8;
        ModularPanel panel = GalaxiaGuiStyle.panel(name, WIDTH, inventoryY + 83);
        panel.child(
            GalaxiaGuiStyle.title(titleKey)
                .pos(8, 7));
        panel.child(
            new Widget<>().size(6)
                .pos(WIDTH - 14, 9)
                .background(
                    new DynamicDrawable(
                        () -> active.getBoolValue() ? EnumTextures.GUI_LED_ON.getImage()
                            : EnumTextures.GUI_LED_OFF.getImage())));
        meter(panel, 20, "galaxia.gui.common.energy", energy, maxEnergy, GalaxiaGuiStyle.WARN, "EU");
        meter(panel, 40, "galaxia.gui.common.oxygen", oxygen, maxOxygen, GalaxiaGuiStyle.ACCENT, "L");
        panel.child(
            GalaxiaGuiStyle.playerInventory()
                .pos(7, inventoryY));
        return panel;
    }

    /** A labelled meter showing the amount on hover */
    private static void meter(ModularPanel panel, int y, String labelKey, IntSyncValue value, IntSyncValue max,
        int color, String unit) {
        panel.child(
            GalaxiaGuiStyle.label(() -> StatCollector.translateToLocal(labelKey), () -> GalaxiaGuiStyle.TEXT_DIM, 1f)
                .pos(8, y));
        panel.child(
            GalaxiaGuiStyle
                .bar(() -> max.getIntValue() <= 0 ? 0 : (double) value.getIntValue() / max.getIntValue(), () -> color)
                .size(WIDTH - 16, 6)
                .pos(8, y + 10)
                .tooltipBuilder(
                    t -> t.addLine(
                        IKey.dynamic(
                            () -> String.format("%,d / %,d %s", value.getIntValue(), max.getIntValue(), unit))))
                .tooltipAutoUpdate(true));
    }

    /** Status line: what the machine does while it works, idle otherwise */
    static Widget<?> status(PanelSyncManager syncManager, String workingKey) {
        BooleanSyncValue active = syncManager.findSyncHandler("active", BooleanSyncValue.class);
        return GalaxiaGuiStyle.label(
            () -> StatCollector.translateToLocal(active.getBoolValue() ? workingKey : "galaxia.gui.common.idle"),
            () -> active.getBoolValue() ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.TEXT_DIM,
            1f);
    }
}
