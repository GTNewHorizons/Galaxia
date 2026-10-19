package com.gtnewhorizons.galaxia.compat.gt.station;

import java.util.function.BooleanSupplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;

import gregtech.api.modularui2.GTWidgetThemes;

/**
 * Status line of the station hatch screens, where stocking hatches show whether their ME network is online
 */
final class StationHatchStatus {

    private StationHatchStatus() {}

    static TextWidget<?> create(PanelSyncManager syncManager, BooleanSupplier linked) {
        BooleanSyncValue linkedSync = new BooleanSyncValue(linked::getAsBoolean);
        syncManager.syncValue("stationLinked", linkedSync);
        return IKey
            .dynamic(
                () -> linkedSync.getBoolValue()
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("galaxia.station_hatch.linked")
                    : EnumChatFormatting.DARK_RED + StatCollector.translateToLocal("galaxia.station_hatch.unlinked"))
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE);
    }
}
