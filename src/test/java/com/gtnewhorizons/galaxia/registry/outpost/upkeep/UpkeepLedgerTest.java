package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleAreaEffect;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class UpkeepLedgerTest {

    private static final FluidKey COOLANT = new FluidKey(new Fluid("galaxia.test.coolant"), null);

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
        AutomatedFacility facility = facilityWithModule(moduleWithUpkeep(itemStack, 5L, COOLANT, 250L));

        UpkeepLedger.UpkeepSummary summary = facility.upkeepSummary();

        assertEquals(
            UpkeepAmount.ofWhole(5L),
            summary.itemsPerMinute()
                .get(itemKey));
        assertEquals(
            UpkeepAmount.ofWhole(250L),
            summary.fluidsPerMinute()
                .get(COOLANT));
        assertEquals(
            1,
            summary.moduleDemands()
                .size());
    }

    @Test
    void summaryIgnoresDisabledModules() {
        ModuleInstance module = moduleWithUpkeep(new ItemStack(new Item()), 5L, COOLANT, 250L);
        module.setEnabled(false);
        AutomatedFacility facility = facilityWithModule(module);

        assertTrue(
            facility.upkeepSummary()
                .isEmpty());
    }

    @Test
    void summaryAppliesRegisteredAreaUpkeepModifier() {
        Item item = new Item();
        ItemStack itemStack = new ItemStack(item);
        ItemStackWrapper itemKey = ItemStackWrapper.of(itemStack);
        ModuleInstance source = moduleWithAreaEffect();
        ModuleInstance target = moduleWithUpkeep(itemStack, 5L, COOLANT, 250L);
        AutomatedFacility facility = facilityWithModule(source, target);

        UpkeepLedger.UpkeepSummary summary = facility.upkeepSummary();

        assertEquals(
            UpkeepAmount.ofWhole(4L),
            summary.itemsPerMinute()
                .get(itemKey));
        assertEquals(
            UpkeepAmount.ofWhole(200L),
            summary.fluidsPerMinute()
                .get(COOLANT));
        assertEquals(
            UpkeepAmount.ofWhole(4L),
            summary.moduleDemands()
                .get(0)
                .demand()
                .itemsPerMinute()
                .get(itemKey));
    }

    private static AutomatedFacility facilityWithModule(ModuleInstance module) {
        return facilityWithModule(new ModuleInstance[] { module });
    }

    private static AutomatedFacility facilityWithModule(ModuleInstance... modules) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        for (ModuleInstance module : modules) {
            facility.addModule(module);
        }
        facility.setStationFeatureSalt(neutralFeatureSalt(facility, modules));
        return facility;
    }

    private static long neutralFeatureSalt(AutomatedFacility facility, ModuleInstance... modules) {
        for (long salt = 0; salt < 10_000L; salt++) {
            facility.setStationFeatureSalt(salt);
            if (hasNoPlanetaryFeatures(facility, modules)) return salt;
        }
        throw new AssertionError("Could not find neutral station feature salt");
    }

    private static boolean hasNoPlanetaryFeatures(AutomatedFacility facility, ModuleInstance... modules) {
        for (ModuleInstance module : modules) {
            for (StationTileCoord tile : module.shape()
                .tiles(module.anchor())) {
                if (!facility.planetaryFeaturesAt(tile)
                    .isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ModuleInstance moduleWithUpkeep(ItemStack upkeepItem, long itemAmount, FluidKey fluid,
        long fluidAmount) {
        ModuleTierData tierData = ModuleTierData.builder()
            .addedEnergyCapacity(0L)
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(new Item()), 1L))
            .upkeepItem(upkeepItem, itemAmount)
            .upkeepFluid(fluid, UpkeepAmount.ofWhole(fluidAmount))
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
        module.completeConstruction();
        return module;
    }

    private static ModuleInstance moduleWithAreaEffect() {
        ModuleTierData tierData = ModuleTierData.builder()
            .addedEnergyCapacity(0L)
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(new Item()), 1L))
            .build();
        FacilityModuleRegistry.Definition definition = new FacilityModuleRegistry.Definition(
            FacilityModuleKind.MAINTENANCE_BAY,
            Map.of(ModuleTier.NONE, tierData),
            (module, facility) -> {},
            TestTieredModule::new,
            List.<ModulePanelAction>of(),
            false,
            List.of(ModuleAreaEffect.adjacentUpkeepMultiplier(80)));
        ModuleInstance module = new ModuleInstance(
            ModuleInstance.ID.create(),
            definition,
            StationTileCoord.of(0, 0),
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        module.setComponent(new TestTieredModule());
        module.completeConstruction();
        return module;
    }

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
