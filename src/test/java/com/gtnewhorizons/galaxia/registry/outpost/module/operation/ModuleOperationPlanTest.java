package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleOperationPlanTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void rejectsNullSpec() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationPlan(null, 200, cost(1L), true));
    }

    @Test
    void keepsReserveItemsFlagAsConfigured() {
        ModuleOperationPlan reserved = new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.LuV, HammerVariant.BIG.name()),
            400,
            cost(1L),
            true);
        ModuleOperationPlan notReserved = new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.LuV, HammerVariant.BIG.name()),
            400,
            cost(1L),
            false);

        assertTrue(reserved.reserveItems());
        assertFalse(notReserved.reserveItems());
    }

    @Test
    void rejectsMaterialCostWithZeroAmount() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationPlan(
                new HammerModuleOperation(ModuleTier.IV, HammerVariant.BIG.name()),
                200,
                cost(0L),
                true));
    }

    private static Map<ItemStackWrapper, Long> cost(long amount) {
        return Map.of(ItemStackWrapper.of(new ItemStack(Items.diamond)), amount);
    }
}
