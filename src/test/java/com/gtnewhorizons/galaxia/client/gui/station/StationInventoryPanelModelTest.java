package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationInventoryPanelModelTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
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
    void boundInputsAreInvalidWhenLowerExceedsUpper() {
        assertFalse(StationInventoryPanelModel.boundsInputValid("442", false, 0L, "320", false, 0L));
    }

    @Test
    void boundInputsUseExistingBoundsWhenFieldsAreBlank() {
        assertFalse(StationInventoryPanelModel.boundsInputValid("442", false, 0L, "", true, 320L));
        assertTrue(StationInventoryPanelModel.boundsInputValid("", true, 128L, "320", false, 0L));
    }

    @Test
    void inventoryRowsShowAllItems() {
        AutomatedFacility distributed = facility();
        ItemStackWrapper tracked = new ItemStackWrapper(Items.diamond, 0, null);
        setAmount(distributed, tracked, 5);

        List<StationInventoryPanelModel.InventoryItemRow> rows = StationInventoryPanelModel
            .inventoryRows(distributed.itemSnapshot(), distributed);

        assertEquals(1, rows.size());
        assertEquals(
            tracked,
            rows.get(0)
                .item());
        assertEquals(
            5L,
            rows.get(0)
                .amount());
        assertEquals(
            0L,
            rows.get(0)
                .upkeepReserve());
    }

    @Test
    void inventoryRowsHideZeroStockItems() {
        AutomatedFacility distributed = facility();
        ItemStackWrapper tracked = new ItemStackWrapper(Items.diamond, 0, null);
        setAmount(distributed, tracked, 0);

        assertTrue(
            StationInventoryPanelModel.inventoryRows(distributed.itemSnapshot(), distributed)
                .isEmpty());
    }

    @Test
    void inventoryRowsIncludeCurrentUpkeepItemsWithoutStock() {
        AutomatedFacility distributed = facility();
        ItemStack upkeepStack = new ItemStack(new Item(), 1, 0);
        ItemStackWrapper tracked = ItemStackWrapper.of(upkeepStack);
        distributed.addModule(moduleWithUpkeep(upkeepStack, 1L));

        List<StationInventoryPanelModel.InventoryItemRow> rows = StationInventoryPanelModel
            .inventoryRows(distributed.itemSnapshot(), distributed);

        assertEquals(1, rows.size());
        assertEquals(
            tracked,
            rows.get(0)
                .item());
        assertEquals(
            0L,
            rows.get(0)
                .amount());
        assertEquals(
            10L,
            rows.get(0)
                .upkeepReserve());
    }

    @Test
    void inventoryRowsExposeUpkeepReserveSeparatelyFromManualReserve() {
        AutomatedFacility distributed = facility();
        ItemStack upkeepStack = new ItemStack(new Item(), 1, 0);
        ItemStackWrapper tracked = ItemStackWrapper.of(upkeepStack);
        distributed.addModule(moduleWithUpkeep(upkeepStack, 1L));
        distributed.insert(tracked, 7);
        distributed.applyCommand(
            new FacilityCommand.SetInventoryBound(distributed.assetId, BoundKind.ITEM_LOWER, tracked, 54L),
            FacilityCommand.Authority.NONE);
        distributed.logisticsConfig.set(tracked, new LogisticsResourceConfig(13, 1, false, false));

        List<StationInventoryPanelModel.InventoryItemRow> rows = StationInventoryPanelModel
            .inventoryRows(distributed.itemSnapshot(), distributed);

        assertEquals(1, rows.size());
        StationInventoryPanelModel.InventoryItemRow row = rows.get(0);
        assertEquals(tracked, row.item());
        assertEquals(7L, row.amount());
        assertEquals(13L, row.upkeepReserve());
    }

    @Test
    void upkeepReserveStatusWarnsWhenReserveCoversLessThanTenMinutes() {
        AutomatedFacility distributed = facility();
        ItemStack upkeepStack = new ItemStack(new Item(), 1, 0);
        ItemStackWrapper tracked = ItemStackWrapper.of(upkeepStack);
        distributed.addModule(moduleWithUpkeep(upkeepStack, 2L));
        distributed.logisticsConfig.set(tracked, new LogisticsResourceConfig(13, 1, false, false));

        StationInventoryPanelModel.UpkeepReserveStatus status = StationInventoryPanelModel
            .upkeepReserveStatus(distributed, tracked);

        assertEquals(StationInventoryPanelModel.UpkeepReserveLevel.WARNING, status.level());
        assertEquals("Reserve covers 6.5 min of upkeep.", status.tooltip());
        assertTrue(
            !status.tooltip()
                .contains("Shortage risk."));
    }

    @Test
    void upkeepReserveStatusIsCriticalBelowThreeMinutes() {
        AutomatedFacility distributed = facility();
        ItemStack upkeepStack = new ItemStack(new Item(), 1, 0);
        ItemStackWrapper tracked = ItemStackWrapper.of(upkeepStack);
        distributed.addModule(moduleWithUpkeep(upkeepStack, 2L));
        distributed.logisticsConfig.set(tracked, new LogisticsResourceConfig(5, 1, false, false));

        StationInventoryPanelModel.UpkeepReserveStatus status = StationInventoryPanelModel
            .upkeepReserveStatus(distributed, tracked);

        assertEquals(StationInventoryPanelModel.UpkeepReserveLevel.CRITICAL, status.level());
        assertEquals("Reserve covers 2.5 min of upkeep.", status.tooltip());
    }

    @Test
    void upkeepOverviewRowsExposeDemandStockReserveAndAutoOrder() {
        AutomatedFacility distributed = facility();
        ItemStack upkeepStack = new ItemStack(new Item(), 1, 0);
        ItemStackWrapper tracked = ItemStackWrapper.of(upkeepStack);
        distributed.addModule(moduleWithUpkeep(upkeepStack, 2L));
        distributed.insert(tracked, 7);
        distributed.logisticsConfig.set(tracked, new LogisticsResourceConfig(13, 1, true, false));

        List<StationInventoryPanelModel.UpkeepItemRow> rows = StationInventoryPanelModel.upkeepItemRows(distributed);

        assertEquals(1, rows.size());
        StationInventoryPanelModel.UpkeepItemRow row = rows.get(0);
        assertEquals(tracked, row.item());
        assertEquals(
            "2",
            row.perMinute()
                .toDisplayString());
        assertEquals(7L, row.stock());
        assertEquals(13L, row.reserve());
        assertTrue(row.autoOrder());
        assertEquals(
            StationInventoryPanelModel.UpkeepReserveLevel.WARNING,
            row.status()
                .level());
    }

    private static void setAmount(AutomatedFacility facility, ItemStackWrapper item, int amount) {
        facility.insert(item, amount);
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            com.gtnewhorizons.galaxia.registry.interfaces.Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance moduleWithUpkeep(ItemStack upkeepItem, long itemAmount) {
        ModuleTierData tierData = ModuleTierData.builder()
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(new Item()), 1L))
            .upkeepItem(upkeepItem, itemAmount)
            .build();
        FacilityModuleRegistry.Definition definition = new FacilityModuleRegistry.Definition(
            FacilityModuleKind.POWER,
            Map.of(ModuleTier.NONE, tierData),
            TestTieredModule::new,
            List.<ModulePanelAction>of(),
            false,
            List.of(),
            null);
        ModuleInstance module = new ModuleInstance(
            ModuleInstance.ID.create(),
            definition,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        module.setComponent(new TestTieredModule());
        module.completeConstruction();
        return module;
    }

    @Test
    void fluidRowsShowStoredFluids() {
        AutomatedFacility distributed = facility();
        FluidKey water = new FluidKey(new Fluid("water"), null);
        addFluid(distributed, water, 1000);

        List<StationInventoryPanelModel.FluidRow> rows = StationInventoryPanelModel
            .fluidRows(distributed.fluidAmounts());

        assertEquals(1, rows.size());
        assertEquals(
            1000L,
            rows.get(0)
                .amount());
    }

    @Test
    void fluidRowsHideZeroAmountFluids() {
        AutomatedFacility distributed = facility();
        FluidKey water = new FluidKey(new Fluid("water"), null);
        addFluid(distributed, water, 0);

        assertTrue(
            StationInventoryPanelModel.fluidRows(distributed.fluidAmounts())
                .isEmpty());
    }

    private static void addFluid(AutomatedFacility facility, FluidKey fluid, int amount) {
        facility.insert(fluid, amount);
    }

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
