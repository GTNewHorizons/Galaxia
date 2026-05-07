package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class ModuleOperationPlanTest {

    @Test
    void rejectsMalformedTargetSpec() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationTargetSpec(
                null,
                FacilityModuleKind.HAMMER,
                ModuleTier.EV,
                FacilityModuleKind.HAMMER,
                ModuleTier.IV,
                "BASE"));

        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationTargetSpec(
                ModuleOperationKind.UPGRADE_REBUILD,
                null,
                ModuleTier.EV,
                FacilityModuleKind.HAMMER,
                ModuleTier.IV,
                "BASE"));

        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleOperationTargetSpec(
                ModuleOperationKind.UPGRADE_REBUILD,
                FacilityModuleKind.HAMMER,
                ModuleTier.EV,
                FacilityModuleKind.HAMMER,
                ModuleTier.IV,
                " "));
    }

    @Test
    void rejectsMalformedPlan() {
        ModuleOperationTargetSpec target = new ModuleOperationTargetSpec(
            ModuleOperationKind.UPGRADE_REBUILD,
            FacilityModuleKind.HAMMER,
            ModuleTier.EV,
            FacilityModuleKind.HAMMER,
            ModuleTier.IV,
            "BIG");

        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationPlan(target, -1, 80, true));
        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationPlan(target, 200, -1, true));
        assertThrows(IllegalArgumentException.class, () -> new ModuleOperationPlan(target, 200, 101, true));
    }

    @Test
    void keepsReserveItemsFlagAsConfigured() {
        ModuleOperationTargetSpec target = new ModuleOperationTargetSpec(
            ModuleOperationKind.UPGRADE_REBUILD,
            FacilityModuleKind.HAMMER,
            ModuleTier.EV,
            FacilityModuleKind.HAMMER,
            ModuleTier.LuV,
            "BIG");

        ModuleOperationPlan reserved = new ModuleOperationPlan(target, 400, 80, true);
        ModuleOperationPlan notReserved = new ModuleOperationPlan(target, 400, 80, false);

        assertTrue(reserved.reserveItems());
        assertFalse(notReserved.reserveItems());
    }
}
