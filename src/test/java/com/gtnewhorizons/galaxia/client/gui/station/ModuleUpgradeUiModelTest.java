package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleUpgradeUiModelTest {

    @BeforeAll
    static void initRegistry() {
        FacilityModuleRegistry.init();
    }

    @Test
    void hammerVariantFiltersAllowedTierOptions() {
        assertEquals(
            List.of(ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV),
            ModuleUpgradeUiModel.hammerAllowedTiers(HammerVariant.BASE));
        assertEquals(
            List.of(ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV),
            ModuleUpgradeUiModel.hammerAllowedTiers(HammerVariant.BIG));
    }

    @Test
    void hammerSelectionNormalizesTierWhenVariantChanges() {
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.hammer(HammerVariant.BASE, ModuleTier.IV);

        ModuleUpgradeSelection normalized = ModuleUpgradeUiModel
            .selectOption(hammerModule(), selection, ModuleUpgradeUiModel.GROUP_HAMMER_VARIANT, HammerVariant.BIG.name());

        assertEquals(HammerVariant.BIG.name(), normalized.get(ModuleUpgradeUiModel.GROUP_HAMMER_VARIANT));
        assertEquals(ModuleTier.LuV.name(), normalized.get(ModuleUpgradeUiModel.GROUP_HAMMER_TIER));
    }

    @Test
    void hammerTierOptionsExposeDisabledBlockedTiers() {
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.hammer(HammerVariant.BIG, ModuleTier.ZPM);

        ModuleUpgradeGroup tierGroup = ModuleUpgradeUiModel.groups(hammerModule(), selection)
            .stream()
            .filter(group -> group.id()
                .equals(ModuleUpgradeUiModel.GROUP_HAMMER_TIER))
            .findFirst()
            .orElseThrow();

        ModuleUpgradeOption ev = tierGroup.options()
            .stream()
            .filter(option -> option.id()
                .equals(ModuleTier.EV.name()))
            .findFirst()
            .orElseThrow();
        ModuleUpgradeOption zpm = tierGroup.options()
            .stream()
            .filter(option -> option.id()
                .equals(ModuleTier.ZPM.name()))
            .findFirst()
            .orElseThrow();

        assertFalse(ev.enabled());
        assertTrue(zpm.enabled());
        assertTrue(zpm.selected());
    }

    private static ModuleInstance hammerModule() {
        return FacilityModuleKind.HAMMER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.IV);
    }
}
