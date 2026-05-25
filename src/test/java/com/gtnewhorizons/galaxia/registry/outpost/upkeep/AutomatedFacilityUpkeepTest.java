package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleState;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class AutomatedFacilityUpkeepTest {

    private static final ItemStack UPKEEP_STACK = new ItemStack(new Item());
    private static final ItemStackWrapper UPKEEP_ITEM = ItemStackWrapper.of(UPKEEP_STACK);

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void upkeepReserveDefaultsToTenMinutesOfCurrentDemand() {
        AutomatedFacility facility = facilityWithModules(moduleWithUpkeep(ModulePriority.NORMAL, "2"));

        assertEquals(20L, facility.upkeepReserve(UPKEEP_ITEM));
    }

    @Test
    void manualUpkeepReserveOverridesDefaultDemandReserve() {
        AutomatedFacility facility = facilityWithModules(moduleWithUpkeep(ModulePriority.NORMAL, "2"));

        facility.setUpkeepReserve(UPKEEP_ITEM, 7L);

        assertEquals(7L, facility.upkeepReserve(UPKEEP_ITEM));
    }

    @Test
    void minuteTickConsumesWholeItemAndStoresFractionalCredit() {
        AutomatedFacility facility = facilityWithModules(moduleWithUpkeep(ModulePriority.NORMAL, "0.1"));
        facility.updateItems(UPKEEP_ITEM, 1);

        tickUpkeepMinute(facility);

        assertEquals(0L, facility.getItemAmount(UPKEEP_ITEM));
        assertEquals(
            "0.9",
            facility.upkeepCredits()
                .itemCredit(UPKEEP_ITEM)
                .toDisplayString());
        assertEquals(
            BlockingReason.NONE,
            facility.modules()
                .get(0)
                .blocking());
    }

    @Test
    void shortageBlocksOnlyUnpaidModulesWithoutItemDebt() {
        ModuleInstance high = moduleWithUpkeep(ModulePriority.HIGH, "0.6");
        ModuleInstance normal = moduleWithUpkeep(ModulePriority.NORMAL, "0.6");
        ModuleInstance low = moduleWithUpkeep(ModulePriority.LOW, "0.6");
        AutomatedFacility facility = facilityWithModules(low, normal, high);
        facility.updateItems(UPKEEP_ITEM, 1);

        tickUpkeepMinute(facility);

        assertEquals(0L, facility.getItemAmount(UPKEEP_ITEM));
        assertEquals(BlockingReason.NONE, high.blocking());
        assertEquals(BlockingReason.UPKEEP_SHORTAGE, normal.blocking());
        assertEquals(BlockingReason.UPKEEP_SHORTAGE, low.blocking());
        assertEquals(ModuleState.BLOCKED, normal.state());
        assertEquals(ModuleState.BLOCKED, low.state());
        assertEquals(
            "0.4",
            facility.upkeepCredits()
                .itemCredit(UPKEEP_ITEM)
                .toDisplayString());
    }

    private static void tickUpkeepMinute(AutomatedFacility facility) {
        for (int i = 0; i < AutomatedFacility.UPKEEP_INTERVAL_TICKS; i++) {
            facility.tick();
        }
    }

    private static AutomatedFacility facilityWithModules(ModuleInstance... modules) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        for (ModuleInstance module : modules) {
            facility.addModule(module);
        }
        return facility;
    }

    private static ModuleInstance moduleWithUpkeep(ModulePriority priority, String itemAmount) {
        ModuleTierData tierData = ModuleTierData.builder()
            .addedEnergyCapacity(0L)
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(new Item()), 1L))
            .upkeepItem(UPKEEP_STACK, itemAmount)
            .build();
        FacilityModuleRegistry.Definition definition = new FacilityModuleRegistry.Definition(
            FacilityModuleKind.POWER,
            Map.of(ModuleTier.NONE, tierData),
            (module, facility) -> {},
            TestTieredModule::new,
            List.<ModulePanelAction>of(),
            false,
            List.of());
        ModuleInstance module = new ModuleInstance(
            ModuleInstance.ID.create(),
            definition,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        module.setComponent(new TestTieredModule());
        module.setPriorityOverride(priority);
        module.completeConstruction();
        return module;
    }

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
