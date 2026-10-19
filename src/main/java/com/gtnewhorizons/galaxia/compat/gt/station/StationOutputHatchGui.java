package com.gtnewhorizons.galaxia.compat.gt.station;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.utils.fluid.FluidInteractions;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/**
 * Station output hatch screen, laid out like the stocking input hatch: the stock slots on the left say which fluid
 * each tank keeps, the tanks on the right show what it holds
 */
class StationOutputHatchGui extends MTEHatchBaseGui<MTEHatchStationOutputHatch> {

    private static final int GRID = 4;

    StationOutputHatchGui(MTEHatchStationOutputHatch hatch) {
        super(hatch);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .crossAxisAlignment(Alignment.CrossAxis.START);
        mainRow.child(createStockSlots(syncManager));
        mainRow.child(
            Flow.col()
                .width(18)
                .mainAxisAlignment(Alignment.MainAxis.START)
                .coverChildrenHeight()
                .child(
                    GTGuiTextures.PICTURE_ARROW_DOUBLE.asWidget()
                        .size(12)
                        .margin(3)));
        mainRow.child(createHeldSlots(syncManager));
        return super.createContentSection(panel, syncManager).child(mainRow);
    }

    /** Ghost slots: clicking with a fluid container marks the fluid, nothing real goes in or comes out */
    private Grid createStockSlots(PanelSyncManager syncManager) {
        syncManager.registerSlotGroup("station_stock", GRID);
        return new Grid().coverChildren()
            .gridOfWidthHeight(GRID, GRID, (x, y, index) -> new FluidSlot() {

                @Override
                protected void addToolTip(RichTooltip tooltip) {
                    FluidStack fluid = getFluidStack();
                    if (fluid != null) {
                        tooltip.addFromFluid(fluid);
                        tooltip.addLine(IKey.lang("modularui2.fluid.phantom.clear"));
                    } else {
                        tooltip.addLine(IKey.lang("modularui2.fluid.empty"));
                    }
                }
            }.syncHandler(new FluidSlotSyncHandler(new StockTank(index)) {

                @Override
                public void tryScrollPhantom(MouseData mouseData) {}

                @Override
                protected void tryClickPhantom(MouseData mouseData, ItemStack cursorStack) {
                    if (mouseData.mouseButton != 0) return;
                    FluidStack held = FluidInteractions.getFluidForItem(cursorStack);
                    if (held != null && isStocked(held)) return;
                    machine.setStock(index, held);
                }
            }.phantom(true)
                .controlsAmount(false))
                .backgroundOverlay(GTGuiTextures.SLOT_FLUID_DARK, GTGuiTextures.OVERLAY_SLOT_ARROW_ME));
    }

    private boolean isStocked(FluidStack fluid) {
        for (int tank = 0; tank < MTEHatchStationOutputHatch.TANKS; tank++) {
            if (GTUtility.areFluidsEqual(machine.getStock(tank), fluid)) return true;
        }
        return false;
    }

    /** The hatch's tanks, shown but not touched */
    private Grid createHeldSlots(PanelSyncManager syncManager) {
        syncManager.registerSlotGroup("station_held", GRID);
        return new Grid().coverChildren()
            .gridOfWidthHeight(
                GRID,
                GRID,
                (x, y, index) -> new FluidSlot().syncHandler(new FluidSlotSyncHandler(machine.getFluidTanks()[index]) {

                    @Override
                    protected void tryClickPhantom(MouseData mouseData, ItemStack cursorStack) {}

                    @Override
                    public void tryScrollPhantom(MouseData mouseData) {}
                }.phantom(true))
                    .backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK));
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + SLOT_SIZE;
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomLeftCornerFlow(panel, syncManager)
            .child(StationHatchStatus.create(syncManager, machine::isLinked));
    }

    /** What a tank keeps in stock, read only */
    private class StockTank implements IFluidTank {

        private final int tank;

        StockTank(int tank) {
            this.tank = tank;
        }

        @Override
        public FluidStack getFluid() {
            return machine.getStock(tank);
        }

        @Override
        public int getFluidAmount() {
            return getFluid() == null ? 0 : 1;
        }

        @Override
        public int getCapacity() {
            return 1;
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(this);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }
}
