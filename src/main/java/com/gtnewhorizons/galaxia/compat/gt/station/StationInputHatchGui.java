package com.gtnewhorizons.galaxia.compat.gt.station;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/**
 * Station input hatch screen, laid out like a GT input hatch. It is a GT output hatch underneath, but its output modes
 * and fluid lock do not apply, everything it takes goes into the station
 */
class StationInputHatchGui extends MTEHatchBaseGui<MTEHatchStationInputHatch> {

    private BooleanSyncValue linked;

    StationInputHatchGui(MTEHatchStationInputHatch hatch) {
        super(hatch);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(1);
        mainRow.child(createScreen(panel, syncManager, machine.getFluidTank()));
        mainRow.child(
            createIO(panel, syncManager, machine.getInputSlot(), machine.getOutputSlot(), machine.getFluidTank()));
        return super.createContentSection(panel, syncManager).child(mainRow);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        linked = StationHatchStatus.sync(syncManager, machine::isLinked);
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
