package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationInventoryPanelModelTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
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
        IDistributedInventory distributed = distributed();
        ItemStackWrapper tracked = new ItemStackWrapper(Items.diamond, 0, null);
        setAmount(distributed, tracked, 5);

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
        IDistributedInventory distributed = distributed();
        ItemStackWrapper tracked = new ItemStackWrapper(Items.diamond, 0, null);
        setAmount(distributed, tracked, 0);

        assertTrue(
            StationInventoryPanelModel.inventoryRows(distributed)
                .isEmpty());
    }

    private static void setAmount(IDistributedInventory distributed, ItemStackWrapper item, int amount) {
        if (distributed instanceof AutomatedFacility af) {
            af.updateItems(item, amount);
        }
    }

    private static IDistributedInventory distributed() {
        return new AutomatedFacility(
            com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID.create(),
            com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId.PROXIMA_CENTAURI,
            com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Kind.AUTOMATED_OUTPOST,
            com.gtnewhorizons.galaxia.registry.interfaces.Buildable.Status.OPERATIONAL);
    }

    @Test
    void fluidRowsShowStoredFluids() {
        IDistributedInventory distributed = distributed();
        FluidKey water = new FluidKey(new Fluid("water"), null);
        addFluid(distributed, water, 1000);

        List<StationInventoryPanelModel.FluidRow> rows = StationInventoryPanelModel.fluidRows(distributed);

        assertEquals(1, rows.size());
        assertEquals(
            1000L,
            rows.get(0)
                .amount());
    }

    @Test
    void fluidRowsHideZeroAmountFluids() {
        IDistributedInventory distributed = distributed();
        FluidKey water = new FluidKey(new Fluid("water"), null);
        addFluid(distributed, water, 0);

        assertTrue(
            StationInventoryPanelModel.fluidRows(distributed)
                .isEmpty());
    }

    private static void addFluid(IDistributedInventory distributed, FluidKey fluid, int amount) {
        if (distributed instanceof AutomatedFacility af) {
            af.updateFluids(fluid, amount);
        }
    }
}
