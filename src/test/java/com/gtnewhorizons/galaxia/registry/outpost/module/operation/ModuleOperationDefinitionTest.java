package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleOperationDefinitionTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void tierDataValidatesBuildTicks() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleTierData(1000L, 0L, 10, null, null, Map.of(new ItemStack(Items.diamond), 1L), 0, 80));
    }

    @Test
    void tierDataValidatesRefundPercent() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleTierData(1000L, 0L, 10, null, null, Map.of(new ItemStack(Items.diamond), 1L), 200, -1));
    }

    @Test
    void hammerSpecRejectsBlankVariant() {
        assertThrows(IllegalArgumentException.class, () -> new HammerModuleOperation(ModuleTier.EV, " "));
    }

    @Test
    void planHasCorrectTimingAndRefund() {
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.IV, HammerVariant.BIG.name()),
            120,
            cost(4L),
            true);

        assertEquals(120, plan.buildTicks());
        assertEquals(true, plan.reserveItems());
    }

    @Test
    void materialCostIsDefensivelyCopied() {
        ItemStack stack = new ItemStack(Items.diamond);
        Map<ItemStackWrapper, Long> cost = cost(6L);
        stack.stackSize = 32;

        Map<ItemStackWrapper, Long> planCost = new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.IV, HammerVariant.BIG.name()),
            120,
            cost,
            false).materialCost();

        assertEquals(1, planCost.size());
        assertEquals(
            6L,
            planCost.values()
                .iterator()
                .next());
    }

    private static Map<ItemStackWrapper, Long> cost(long amount) {
        return Map.of(ItemStackWrapper.of(new ItemStack(Items.diamond)), amount);
    }
}
