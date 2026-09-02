package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityModuleRegistryTest {

    private static final FluidKey COOLANT = new FluidKey(new Fluid("galaxia.test.coolant"), null);

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void registeredModulesExposeDefaultUpgradeTemplate() {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(FacilityModuleKind.HAMMER);

        ModuleTierData tierData = definition.getTierData(ModuleTier.LuV);
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new IModuleOperation.Hammer(ModuleTier.LuV, "BIG"),
            tierData.buildTicks(),
            Map.of(),
            false);

        assertEquals(
            ModuleTier.LuV,
            plan.spec()
                .targetTier());
        assertEquals("BIG", ((IModuleOperation.Hammer) plan.spec()).targetVariantKey());
        assertEquals(200, plan.buildTicks());
        assertFalse(plan.reserveItems());
    }

    @Test
    void moduleCreationRejectsTierNotAllowedByKind() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FacilityModuleRegistry
                .create(ModuleInstance.ID.create(), FacilityModuleKind.POWER, null, ModuleShape.SINGLE, ModuleTier.HV));
    }

    @Test
    void tierDataCarriesBuildTicksAndRefundPercent() {
        ModuleTierData data = new ModuleTierData(0L, 10, null, null, Map.of(new ItemStack(Items.diamond), 1L), 40, 50);

        assertEquals(40, data.buildTicks());
        assertEquals(50, data.completionRefundPercent());
    }

    @Test
    void tierDataBuilderUsesNamedFieldsWithDefaults() {
        ItemStack material = new ItemStack(Items.diamond);

        ModuleTierData data = ModuleTierData.builder()
            .powerDraw(32L)
            .cooldown(10)
            .capacity(4096L)
            .cost(Map.of(material, 2L))
            .build();

        assertEquals(32L, data.powerDrawEuPerTick());
        assertEquals(10, data.cooldownTicks());
        assertEquals(4096L, data.capacity());
        assertEquals(
            2L,
            data.constructionCost()
                .get(material));
        assertEquals(200, data.buildTicks());
        assertEquals(80, data.completionRefundPercent());
        assertTrue(
            data.upkeepDemand()
                .isEmpty());
    }

    @Test
    void tierDataBuilderCarriesUpkeepDemand() {
        ItemStack material = new ItemStack(new Item());
        ItemStack upkeep = new ItemStack(new Item());

        ModuleTierData data = ModuleTierData.builder()
            .powerDraw(32L)
            .cooldown(10)
            .cost(Map.of(material, 2L))
            .upkeepItem(upkeep, 3L)
            .upkeepFluid(COOLANT, UpkeepAmount.ofWhole(1000L))
            .build();

        assertEquals(
            UpkeepAmount.ofWhole(3L),
            data.upkeepDemand()
                .itemsPerMinute()
                .get(ItemStackWrapper.of(upkeep)));
        assertEquals(
            UpkeepAmount.ofWhole(1000L),
            data.upkeepDemand()
                .fluidsPerMinute()
                .get(COOLANT));
    }

    @Test
    void tierDataBuilderCarriesVariantCooldownsAndOperationSettings() {
        ItemStack material = new ItemStack(Items.diamond);

        ModuleTierData data = ModuleTierData.builder()
            .powerDraw(64L)
            .cooldown(20)
            .variantCooldowns(Map.of("BIG", 600))
            .chargeTicks(400)
            .variantChargeTicks(Map.of("BIG", 800))
            .cost(Map.of(material, 4L))
            .buildTicks(40)
            .refundPercent(50)
            .build();

        assertEquals(
            600,
            data.variantCooldowns()
                .get("BIG"));
        assertEquals(400, data.chargeTicks());
        assertEquals(
            800,
            data.variantChargeTicks()
                .get("BIG"));
        assertEquals(40, data.buildTicks());
        assertEquals(50, data.completionRefundPercent());
    }

    @Test
    void tierDataIsAccessible() {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(FacilityModuleKind.STORAGE);
        ModuleTierData hvData = definition.getTierData(ModuleTier.HV);

        assertEquals(1024L, hvData.capacity());
        assertEquals(0L, hvData.powerDrawEuPerTick());

        ModuleTierData ivData = definition.getTierData(ModuleTier.IV);
        assertEquals(16384L, ivData.capacity());
    }

    @Test
    void moduleDefinitionsExposeRegisteredPanelActions() {
        assertEquals(
            List.of(ModulePanelAction.CONFIG, ModulePanelAction.UPGRADE),
            FacilityModuleRegistry.get(FacilityModuleKind.MINER)
                .panelActions());
        assertEquals(
            List.of(ModulePanelAction.CONFIG, ModulePanelAction.UPGRADE),
            FacilityModuleRegistry.get(FacilityModuleKind.HAMMER)
                .panelActions());
        assertEquals(
            List.of(),
            FacilityModuleRegistry.get(FacilityModuleKind.POWER)
                .panelActions());
    }

    @Test
    void moduleDefinitionsExposeSettingsGroupSupport() {
        assertTrue(
            FacilityModuleRegistry.get(FacilityModuleKind.MINER)
                .settingsGroups());
        if (FacilityModuleKind.MACERATOR.isAvailable()) {
            assertTrue(
                FacilityModuleRegistry.get(FacilityModuleKind.MACERATOR)
                    .settingsGroups());
            assertTrue(
                FacilityModuleRegistry.get(FacilityModuleKind.CENTRIFUGE)
                    .settingsGroups());
        }
        assertFalse(
            FacilityModuleRegistry.get(FacilityModuleKind.HAMMER)
                .settingsGroups());
    }

    @Test
    void facilityReportsOperationalRecipeModulesAsProduction() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(0, 0),
            ModuleShape.SINGLE,
            ModuleTier.HV);
        module.completeConstruction();

        assertTrue(facilityWith(module).hasProductionCapability());
    }

    @Test
    void facilityReportsOperationalMinerAsProduction() {
        ModuleInstance miner = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.of(0, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        miner.completeConstruction();

        assertTrue(facilityWith(miner).hasProductionCapability());
    }

    private static AutomatedFacility facilityWith(ModuleInstance module) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.addModule(module);
        return facility;
    }

    @Test
    void geothermalGeneratorIsThreeByThreePowerModuleAnchoredOnMagmaPool() {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry
            .get(FacilityModuleKind.GEOTHERMAL_GENERATOR);

        assertEquals(ModuleShape.BLOCK_3x3, FacilityModuleKind.GEOTHERMAL_GENERATOR.defaultShape());
        assertEquals(ModuleTier.HV, FacilityModuleKind.GEOTHERMAL_GENERATOR.defaultTier());
        assertEquals(StationModuleCategory.POWER, FacilityModuleKind.GEOTHERMAL_GENERATOR.getCategory());
        assertEquals(
            PlanetaryFeatureRegistry.MAGMA_POOL.key(),
            FacilityModuleKind.GEOTHERMAL_GENERATOR.requiredAnchorFeature());
        assertEquals(
            -8192L,
            definition.getTierData(ModuleTier.HV)
                .powerDrawEuPerTick());
    }
}
