package com.gtnewhorizons.galaxia.compat.gt.station;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.MTEHatchOutputBusGui;
import gregtech.common.gui.modularui.util.FilterSlot;

/**
 * Station input bus screen: the screen of a GT output bus, with the filter slot showing its filter icon and tooltip,
 * which GT leaves out when the slot is bound to a bus
 */
class StationInputBusGui extends MTEHatchOutputBusGui {

    private BooleanSyncValue linked;

    StationInputBusGui(MTEHatchStationInputBus hatch) {
        super(hatch);
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        Flow flow = super.createBottomLeftCornerFlow(panel, syncManager);
        for (IWidget child : flow.getChildren()) {
            if (!(child instanceof FilterSlot filter)) continue;
            filter.background(GuiTextures.SLOT_ITEM, GTGuiTextures.OVERLAY_SLOT_FILTER);
            filter.itemTooltip()
                .tooltipBuilder(tooltip -> tooltip.add(IKey.lang("GT5U.bus.filterTooltip.full")));
            filter.tooltip(tooltip -> tooltip.add(IKey.lang("GT5U.bus.filterTooltip.empty")));
        }
        return flow;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        linked = StationHatchStatus.sync(syncManager, ((MTEHatchStationInputBus) machine)::isLinked);
    }

    @Override
    protected boolean supportsTopRightCornerFlow() {
        return true;
    }

    @Override
    protected Flow createTopRightCornerFlow() {
        return StationHatchStatus.corner(linked);
    }
}
