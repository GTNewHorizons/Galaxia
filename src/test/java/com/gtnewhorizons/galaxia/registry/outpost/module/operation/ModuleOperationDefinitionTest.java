package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class ModuleOperationDefinitionTest {

    @Test
    void createPlanUsesDefinitionTimingRefundAndReserveChoice() {
        ModuleOperationDefinition definition = new ModuleOperationDefinition(
            ModuleOperationKind.UPGRADE_REBUILD,
            120,
            75,
            Map.of(new ItemStack(new Item()), 4L));
        ModuleOperationTargetSpec target = hammerTarget();

        ModuleOperationPlan plan = definition.createPlan(target, true);

        assertSame(target, plan.targetSpec());
        assertEquals(120, plan.buildTicks());
        assertEquals(75, plan.completionRefundPercent());
        assertTrue(plan.reserveItems());
    }

    @Test
    void materialCostIsResolvedAndDefensivelyCopied() {
        ItemStack stack = new ItemStack(new Item());
        ModuleOperationDefinition definition = new ModuleOperationDefinition(
            ModuleOperationKind.UPGRADE_REBUILD,
            120,
            80,
            Map.of(stack, 6L));
        stack.stackSize = 32;

        Map<ItemStack, Long> cost = definition.materialCost(hammerTarget());

        assertEquals(1, cost.size());
        assertEquals(
            6L,
            cost.values()
                .iterator()
                .next());
        assertEquals(
            1,
            cost.keySet()
                .iterator()
                .next().stackSize);
    }

    @Test
    void malformedDefinitionOrCostCrashes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationDefinition(null, 120, 80, Map.of(new ItemStack(new Item()), 4L)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationDefinition(
                ModuleOperationKind.UPGRADE_REBUILD,
                0,
                80,
                Map.of(new ItemStack(new Item()), 4L)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationDefinition(
                ModuleOperationKind.UPGRADE_REBUILD,
                120,
                80,
                Map.of(new ItemStack(new Item()), 0L)));
    }

    private static ModuleOperationTargetSpec hammerTarget() {
        return new ModuleOperationTargetSpec(
            ModuleOperationKind.UPGRADE_REBUILD,
            FacilityModuleKind.HAMMER,
            ModuleTier.EV,
            FacilityModuleKind.HAMMER,
            ModuleTier.LuV,
            "BIG");
    }
}
