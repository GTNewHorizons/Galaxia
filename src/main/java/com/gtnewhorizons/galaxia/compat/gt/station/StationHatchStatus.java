package com.gtnewhorizons.galaxia.compat.gt.station;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;

/**
 * Lamp in the top right corner of the station hatch screens, telling on hover whether the hatch reaches a station
 */
final class StationHatchStatus {

    /** Height the lamp takes above content that reaches the right edge of the screen */
    static final int LAMP_SPACE = 8;

    private StationHatchStatus() {}

    static BooleanSyncValue sync(PanelSyncManager syncManager, BooleanSupplier linked) {
        BooleanSyncValue linkedSync = new BooleanSyncValue(linked::getAsBoolean);
        syncManager.syncValue("stationLinked", linkedSync);
        return linkedSync;
    }

    static Flow corner(BooleanSyncValue linked) {
        return Flow.column()
            .coverChildren()
            .topRel(0)
            .rightRel(0)
            .child(
                new Widget<>().size(6)
                    .name("station.link")
                    .background(
                        new DynamicDrawable(
                            () -> (linked.getBoolValue() ? EnumTextures.GUI_STATION_LINK_ON
                                : EnumTextures.GUI_STATION_LINK_OFF).getImage()))
                    .tooltipDynamic(
                        tooltip -> tooltip.setAutoUpdate(true)
                            .add(
                                IKey.lang(
                                    linked.getBoolValue() ? "galaxia.station_hatch.linked"
                                        : "galaxia.station_hatch.unlinked"))));
    }
}
