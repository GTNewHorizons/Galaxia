package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventoryOLD;
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

        IDistributedInventoryOLD distributed = distributed(inventory);
        List<Map.Entry<ItemStackWrapper, Long>> rows = StationInventoryPanelModel.inventoryRows(distributed, inventory);

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
            StationInventoryPanelModel.inventoryRows(distributed(inventory), null)
                .isEmpty());
    }

    private static IDistributedInventoryOLD distributed(AutomatedFacilityInventory inv) {
        return new IDistributedInventoryOLD() {

            @Override
            public Map<ItemStackWrapper, Long> aggregatedItemAmounts() {
                return inv.snapshot();
            }

            @Override
            public long totalItemCount() {
                return inv.totalItems();
            }

            @Override
            public List<IInventory> getInventories() {
                return List.of(inv);
            }

            @Override
            public String getInventoryName() {
                return "test";
            }

            @Override
            public List<ItemStack> getFiltersFor(int i) {
                return List.of();
            }

            @Override
            public void setFilters(int slot, List<ItemStack> filterList) {}

            @Override
            public void addFilter(int slot, ItemStack filter) {}

            @Override
            public void removeFilter(int slot, ItemStack filter) {}

            @Override
            public void clearFilters(int slot) {}

            @Override
            public Map<Integer, List<ItemStack>> filtersSnapshot() {
                return Map.of();
            }
        };
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
