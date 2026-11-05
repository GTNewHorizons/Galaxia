package com.gtnewhorizons.galaxia.compat.gt.station;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.gui.modularui.util.StockingSlot;
import gregtech.common.modularui2.widget.builder.ItemSlotGridBuilder;

/**
 * Station output bus screen, laid out like the stocking input bus: the stock slots on the left say what the bus keeps,
 * the slots on the right show what it holds
 */
class StationOutputBusGui extends MTEHatchBaseGui<MTEHatchStationOutputBus> {

    private BooleanSyncValue linked;

    private static final int GRID = 4;

    StationOutputBusGui(MTEHatchStationOutputBus hatch) {
        super(hatch);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        // The slots reach the right edge, so they start below the status lamp
        Flow mainRow = Flow.row()
            .coverChildren()
            .marginTop(StationHatchStatus.LAMP_SPACE);
        mainRow.child(createStockSlots(syncManager));
        mainRow.child(createMiddleColumn(syncManager));
        mainRow.child(createHeldSlots(syncManager));
        return super.createContentSection(panel, syncManager).child(mainRow);
    }

    /** Ghost slots: clicking with an item marks its kind, nothing real goes in or comes out */
    private Grid createStockSlots(PanelSyncManager syncManager) {
        IItemHandlerModifiable stock = new IItemHandlerModifiable() {

            @Override
            public int getSlots() {
                return machine.getStockSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return machine.getStock(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                // Like the stocking bus: phantom clicks only go ahead when the slot seems to take the item
                return null;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return null;
            }

            @Override
            public int getSlotLimit(int slot) {
                return Integer.MAX_VALUE;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                machine.setStock(slot, stack);
            }
        };
        // Never locked, the station has no auto-pull
        BooleanSyncValue locked = new BooleanSyncValue(() -> false);
        return new ItemSlotGridBuilder(stock, syncManager).size(GRID)
            .slotGroupKey("station_stock")
            .filter(this::isNotStocked)
            .itemSlotSupplier(new Supplier<>() {

                private int created;

                @Override
                public StockingSlot get() {
                    StockingSlot slot = new StockingSlot(locked);
                    if (created++ == 0) slot.name("station.stock.0");
                    return slot;
                }
            })
            .build();
    }

    private boolean isNotStocked(ItemStack item) {
        for (int slot = 0; slot < machine.getStockSlots(); slot++) {
            if (GTUtility.areStacksEqual(machine.getStock(slot), item)) return false;
        }
        return true;
    }

    private Grid createHeldSlots(PanelSyncManager syncManager) {
        return new ItemSlotGridBuilder(machine.inventoryHandler, syncManager).size(GRID)
            .slotGroupKey("station_held")
            // Items are taken out by hand, only the stock slots put them in
            .accessibility(false, true)
            .itemSlotSupplier(new Supplier<>() {

                private int created;

                @Override
                public ItemSlot get() {
                    ItemSlot slot = new ItemSlot().backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK);
                    if (created++ == 0) slot.name("station.held.0");
                    return slot;
                }
            })
            .build();
    }

    private Flow createMiddleColumn(PanelSyncManager syncManager) {
        return Flow.col()
            .coverChildren()
            .reverseLayout()
            .mainAxisAlignment(Alignment.MainAxis.END)
            .child(createCircuitSlot(syncManager))
            .child(
                GTGuiTextures.PICTURE_ARROW_DOUBLE.asWidget()
                    .size(12)
                    .margin(3));
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + SLOT_SIZE + StationHatchStatus.LAMP_SPACE;
    }

    @Override
    protected Flow createBottomRightCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        // The circuit slot sits in the middle column
        return Flow.row()
            .mainAxisAlignment(Alignment.MainAxis.END)
            .reverseLayout(true)
            .coverChildren()
            .verticalCenter()
            .rightRel(0)
            .child(makeLogoWidget());
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
