package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;

final class FacilityModuleRegistryTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
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
            Map.of(new ItemStack(new Item()), 1L),
            40,
            50);

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
}
