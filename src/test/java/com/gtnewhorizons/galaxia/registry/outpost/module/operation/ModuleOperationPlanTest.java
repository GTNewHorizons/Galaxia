package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class ModuleOperationPlanTest {

    @Test
    void rejectsMalformedDefinition() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationDefinition(null, 200, 80, cost(1L)));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationDefinition(
                ModuleOperationKind.UPGRADE_REBUILD,
                FacilityModuleKind.HAMMER,
                ModuleTier.EV,
                null,
                FacilityModuleKind.HAMMER,
                ModuleTier.IV,
                " ",
                null,
                null,
                null,
                null,
                200,
                80,
                Map.of()));
    }

    @Test
    void rejectsMalformedPlan() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationPlan(null, true));
    }

    @Test
    void keepsReserveItemsFlagAsConfigured() {
        ModuleOperationDefinition definition = new ModuleOperationDefinition(
            ModuleOperationKind.UPGRADE_REBUILD,
            400,
            80,
            cost(1L))
                .withTarget(FacilityModuleKind.HAMMER, ModuleTier.EV, FacilityModuleKind.HAMMER, ModuleTier.LuV, "BIG");

        ModuleOperationPlan reserved = new ModuleOperationPlan(definition, true);
        ModuleOperationPlan notReserved = new ModuleOperationPlan(definition, false);

        assertTrue(reserved.reserveItems());
        assertFalse(notReserved.reserveItems());
    }

    private static Map<ItemStackWrapper, Long> cost(long amount) {
        return Map.of(ItemStackWrapper.of(new ItemStack(new Item())), amount);
    }
}
