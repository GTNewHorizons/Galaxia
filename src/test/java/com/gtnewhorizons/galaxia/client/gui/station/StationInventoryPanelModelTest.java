package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class StationInventoryPanelModelTest {

    @Test
    void allModeVoidsFullRowAmount() {
        assertEquals(128L, StationInventoryPanelModel.voidAmount(false, 128L, "64"));
    }

    @Test
    void amountModeUsesEnteredAmount() {
        assertEquals(32L, StationInventoryPanelModel.voidAmount(true, 128L, "32"));
    }

    @Test
    void amountModeClampsToAvailableAmount() {
        assertEquals(128L, StationInventoryPanelModel.voidAmount(true, 128L, "999"));
    }

    @Test
    void blankAmountVoidsNothing() {
        assertEquals(0L, StationInventoryPanelModel.voidAmount(true, 128L, ""));
    }

    @Test
    void inventoryRowsIncludeItemsWithBoundsButNoStock() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        ItemStackWrapper tracked = new ItemStackWrapper(new Item(), 0, null);
        inventory.setItemLowerBound(tracked, 32);

        List<Map.Entry<ItemStackWrapper, Long>> rows = StationInventoryPanelModel.inventoryRows(inventory);

        assertEquals(1, rows.size());
        assertEquals(
            tracked,
            rows.get(0)
                .getKey());
        assertEquals(
            0L,
            rows.get(0)
                .getValue());
    }

    @Test
    void inventoryRowsHideZeroStockItemsWithoutBounds() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        inventory.setAmount(new ItemStackWrapper(new Item(), 0, null), 0);

        assertTrue(
            StationInventoryPanelModel.inventoryRows(inventory)
                .isEmpty());
    }

    @Test
    void fluidRowsIncludeFluidsWithBoundsButNoStoredAmount() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        inventory.setFluidUpperBound("galaxia.test.fluid", 1000);

        List<StationInventoryPanelModel.FluidRow> rows = StationInventoryPanelModel.fluidRows(inventory);

        assertEquals(1, rows.size());
        assertEquals(
            "galaxia.test.fluid",
            rows.get(0)
                .fluidName());
        assertEquals(
            0L,
            rows.get(0)
                .amount());
    }

    @Test
    void fluidRowsHideZeroAmountFluidsWithoutBounds() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        inventory.addFluid("galaxia.test.fluid", 0);

        assertTrue(
            StationInventoryPanelModel.fluidRows(inventory)
                .isEmpty());
    }
}
