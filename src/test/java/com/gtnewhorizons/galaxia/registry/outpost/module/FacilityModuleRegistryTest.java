package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityModuleRegistryTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void registeredModulesExposeDefaultUpgradeTemplate() {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(FacilityModuleKind.HAMMER);

        ModuleTierData tierData = definition.getTierData(ModuleTier.LuV);
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.LuV, "BIG"),
            tierData.buildTicks(),
            Map.of(),
            false);

        assertEquals(
            ModuleTier.LuV,
            plan.spec()
                .targetTier());
        assertEquals("BIG", ((HammerModuleOperation) plan.spec()).targetVariantKey());
        assertEquals(200, plan.buildTicks());
        assertFalse(plan.reserveItems());
    }

    @Test
    void tierDataCarriesBuildTicksAndRefundPercent() {
        ModuleTierData data = new ModuleTierData(
            1000L,
            0L,
            10,
            null,
            null,
            Map.of(new ItemStack(Items.diamond), 1L),
            40,
            50);

        assertEquals(40, data.buildTicks());
        assertEquals(50, data.completionRefundPercent());
    }

    @Test
    void tierDataBuilderUsesNamedFieldsWithDefaults() {
        ItemStack material = new ItemStack(Items.diamond);

        ModuleTierData data = ModuleTierData.builder()
            .addedEnergyCapacity(1000L)
            .powerDraw(32L)
            .cooldown(10)
            .capacity(4096L)
            .cost(Map.of(material, 2L))
            .build();

        assertEquals(1000L, data.baseEnergyCapacity());
        assertEquals(32L, data.powerDrawEuPerTick());
        assertEquals(10, data.cooldownTicks());
        assertEquals(4096L, data.capacity());
        assertEquals(
            2L,
            data.constructionCost()
                .get(material));
        assertEquals(200, data.buildTicks());
        assertEquals(80, data.completionRefundPercent());
    }

    @Test
    void tierDataBuilderCarriesVariantCooldownsAndOperationSettings() {
        ItemStack material = new ItemStack(Items.diamond);

        ModuleTierData data = ModuleTierData.builder()
            .addedEnergyCapacity(2000L)
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
        assertEquals(500L, hvData.baseEnergyCapacity());
        assertEquals(0L, hvData.powerDrawEuPerTick());

        ModuleTierData ivData = definition.getTierData(ModuleTier.IV);
        assertEquals(16384L, ivData.capacity());
        assertEquals(8000L, ivData.baseEnergyCapacity());
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
