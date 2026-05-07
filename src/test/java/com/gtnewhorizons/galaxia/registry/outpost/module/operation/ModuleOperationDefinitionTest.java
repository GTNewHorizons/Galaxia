package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class ModuleOperationDefinitionTest {

    @Test
    void createPlanUsesDefinitionTimingRefundAndReserveChoice() {
        ModuleOperationDefinition definition = new ModuleOperationDefinition(
            ModuleOperationKind.UPGRADE_REBUILD,
            120,
            75,
            cost(4L));
        ModuleOperationDefinition targetDefinition = hammerDefinition(definition);

        ModuleOperationPlan plan = targetDefinition.createPlan(true);

        assertSame(targetDefinition, plan.definition());
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
            Map.of(ItemStackWrapper.of(stack), 6L));
        stack.stackSize = 32;

        Map<ItemStackWrapper, Long> cost = definition.materialCost();

        assertEquals(1, cost.size());
        assertEquals(
            6L,
            cost.values()
                .iterator()
                .next());
        assertEquals(
            ItemStackWrapper.of(stack),
            cost.keySet()
                .iterator()
                .next());
    }

    @Test
    void malformedDefinitionOrCostCrashes() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationDefinition(null, 120, 80, cost(4L)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationDefinition(ModuleOperationKind.UPGRADE_REBUILD, 0, 80, cost(4L)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationDefinition(ModuleOperationKind.UPGRADE_REBUILD, 120, 80, cost(0L)));
    }

    private static ModuleOperationDefinition hammerDefinition(ModuleOperationDefinition definition) {
        return definition
            .withTarget(FacilityModuleKind.HAMMER, ModuleTier.EV, FacilityModuleKind.HAMMER, ModuleTier.LuV, "BIG");
    }

    private static Map<ItemStackWrapper, Long> cost(long amount) {
        return Map.of(ItemStackWrapper.of(new ItemStack(new Item())), amount);
    }
}
