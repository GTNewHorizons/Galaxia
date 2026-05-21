package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class UpkeepLedgerTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void summaryAggregatesOperationalModuleDemand() {
        Item item = new Item();
        ItemStack itemStack = new ItemStack(item);
        ItemStackWrapper itemKey = ItemStackWrapper.of(itemStack);
        AutomatedFacility facility = facilityWithModule(moduleWithUpkeep(itemStack, 5L, "galaxia.test.coolant", 250L));

        UpkeepLedger.UpkeepSummary summary = facility.upkeepSummary();

        assertEquals(
            5L,
            summary.itemsPerMinute()
                .get(itemKey));
        assertEquals(
            250L,
            summary.fluidsPerMinute()
                .get("galaxia.test.coolant"));
        assertEquals(
            1,
            summary.moduleDemands()
                .size());
    }

    @Test
    void summaryIgnoresDisabledModules() {
        ModuleInstance module = moduleWithUpkeep(new ItemStack(new Item()), 5L, "galaxia.test.coolant", 250L);
        module.setEnabled(false);
        AutomatedFacility facility = facilityWithModule(module);

        assertTrue(
            facility.upkeepSummary()
                .isEmpty());
    }

    private static AutomatedFacility facilityWithModule(ModuleInstance module) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.addModule(module);
        return facility;
    }

    private static ModuleInstance moduleWithUpkeep(ItemStack upkeepItem, long itemAmount, String fluidName,
        long fluidAmount) {
        ModuleTierData tierData = ModuleTierData.builder()
            .addedEnergyCapacity(0L)
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(new Item()), 1L))
            .upkeepItem(upkeepItem, itemAmount)
            .upkeepFluid(fluidName, fluidAmount)
            .build();
        FacilityModuleRegistry.Definition definition = new FacilityModuleRegistry.Definition(
            FacilityModuleKind.POWER,
            Map.of(ModuleTier.NONE, tierData),
            (module, facility) -> {},
            TestTieredModule::new,
            List.<ModulePanelAction>of(),
            false);
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

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
