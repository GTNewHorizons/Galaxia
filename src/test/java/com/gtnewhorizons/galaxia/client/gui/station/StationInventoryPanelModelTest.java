package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.IFluidTank;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.FluidKey;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class StationInventoryPanelModelTest {

    @BeforeAll
    static void init() {
        TestFMLRegistry.init();
    }

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
    void inventoryRowsShowAllItems() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        ItemStackWrapper tracked = new ItemStackWrapper(Items.diamond, 0, null);
        inventory.setAmount(tracked, 5);

        IDistributedInventory distributed = distributed(inventory);
        List<Map.Entry<ItemStackWrapper, Long>> rows = StationInventoryPanelModel.inventoryRows(distributed);

        assertEquals(1, rows.size());
        assertEquals(
            tracked,
            rows.get(0)
                .getKey());
        assertEquals(
            5L,
            rows.get(0)
                .getValue());
    }

    @Test
    void inventoryRowsHideZeroStockItems() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        inventory.setAmount(new ItemStackWrapper(Items.diamond, 0, null), 0);

        assertTrue(
            StationInventoryPanelModel.inventoryRows(distributed(inventory))
                .isEmpty());
    }

    private static IDistributedInventory distributed(AutomatedFacilityInventory inv) {
        return new IDistributedInventory() {

            @Override
            public Map<ItemStackWrapper, Long> aggregatedItems() {
                return inv.snapshot();
            }

            @Override
            public long totalItemsStored() {
                return inv.totalItems();
            }

            @Override
            public List<IInventory> getInventories() {
                return List.of(inv);
            }

            @Override
            public List<IFluidTank> getFluidTanks() {
                return List.of();
            }

            @Override
            public Map<FluidKey, Long> aggregatedFluids() {
                Map<FluidKey, Long> result = new LinkedHashMap<>();
                for (Map.Entry<FluidKey, Long> e : inv.fluidSnapshot()
                    .entrySet()) {
                    result.put(e.getKey(), e.getValue());
                }
                return result;
            }

            public String getInventoryName() {
                return "test";
            }
        };
    }

    @Test
    void fluidRowsShowStoredFluids() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        FluidKey water = new FluidKey(new Fluid("water"), null);
        inventory.addFluid(water, 1000);

        List<StationInventoryPanelModel.FluidRow> rows = StationInventoryPanelModel.fluidRows(distributed(inventory));

        assertEquals(1, rows.size());
        assertEquals(
            1000L,
            rows.get(0)
                .amount());
    }

    @Test
    void fluidRowsHideZeroAmountFluids() {
        AutomatedFacilityInventory inventory = new AutomatedFacilityInventory();
        FluidKey water = new FluidKey(new Fluid("water"), null);
        inventory.addFluid(water, 0);

        assertTrue(
            StationInventoryPanelModel.fluidRows(distributed(inventory))
                .isEmpty());
    }
}
