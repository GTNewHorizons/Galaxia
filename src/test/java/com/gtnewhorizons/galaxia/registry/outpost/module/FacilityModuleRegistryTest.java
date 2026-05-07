package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationTargetSpec;

final class FacilityModuleRegistryTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void registeredModulesExposeDefaultUpgradeDefinition() {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(FacilityModuleKind.HAMMER);
        ModuleOperationTargetSpec target = new ModuleOperationTargetSpec(
            ModuleOperationKind.UPGRADE_REBUILD,
            FacilityModuleKind.HAMMER,
            ModuleTier.EV,
            FacilityModuleKind.HAMMER,
            ModuleTier.LuV,
            "BIG");

        ModuleOperationPlan plan = definition.operationDefinition(ModuleOperationKind.UPGRADE_REBUILD)
            .createPlan(target, false);

        assertSame(target, plan.targetSpec());
        assertEquals(200, plan.buildTicks());
        assertEquals(80, plan.completionRefundPercent());
        assertFalse(plan.reserveItems());
    }

    @Test
    void definitionAllowsOperationDefinitionOverride() {
        Map<ModuleOperationKind, ModuleOperationDefinition> operationDefinitions = Map.of(
            ModuleOperationKind.UPGRADE_REBUILD,
            new ModuleOperationDefinition(
                ModuleOperationKind.UPGRADE_REBUILD,
                40,
                50,
                Map.of(new ItemStack(new Item()), 2L)));

        FacilityModuleRegistry.Definition registryDefinition = new FacilityModuleRegistry.Definition(
            FacilityModuleKind.MAINTENANCE_BAY,
            500L,
            0L,
            100,
            Map.of(new ItemStack(new Item()), 8L),
            operationDefinitions,
            (instance, outpost) -> {},
            ModuleMaintenanceBay::new);

        ModuleOperationDefinition definition = registryDefinition
            .operationDefinition(ModuleOperationKind.UPGRADE_REBUILD);

        assertEquals(40, definition.buildTicks());
        assertEquals(50, definition.completionRefundPercent());
        assertEquals(
            2L,
            definition
                .materialCost(
                    new ModuleOperationTargetSpec(
                        ModuleOperationKind.UPGRADE_REBUILD,
                        FacilityModuleKind.MAINTENANCE_BAY,
                        ModuleTier.NONE,
                        FacilityModuleKind.MAINTENANCE_BAY,
                        ModuleTier.NONE,
                        null))
                .values()
                .iterator()
                .next());
    }
}
