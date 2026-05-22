package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleUpgradeUiModelTest {

    @BeforeAll
    static void initRegistry() {
        GalaxiaTestBootstrap.ensureFacilityModules();
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

        ModuleUpgradeSelection normalized = ModuleUpgradeUiModel.selectOption(
            hammerModule(),
            selection,
            ModuleUpgradeUiModel.GROUP_HAMMER_VARIANT,
            HammerVariant.BIG.name());

        assertEquals(HammerVariant.BIG.name(), normalized.get(ModuleUpgradeUiModel.GROUP_HAMMER_VARIANT));
        assertEquals(ModuleTier.LuV.name(), normalized.get(ModuleUpgradeUiModel.GROUP_HAMMER_TIER));
    }

    @Test
    void hammerTierOptionsExposeDisabledBlockedTiers() {
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.hammer(HammerVariant.BIG, ModuleTier.ZPM);

        ModuleUpgradeGroup tierGroup = ModuleUpgradeUiModel.groups(hammerModule(), selection)
            .stream()
            .filter(
                group -> group.id()
                    .equals(ModuleUpgradeUiModel.GROUP_HAMMER_TIER))
            .findFirst()
            .orElseThrow();

        ModuleUpgradeOption ev = tierGroup.options()
            .stream()
            .filter(
                option -> option.id()
                    .equals(ModuleTier.EV.name()))
            .findFirst()
            .orElseThrow();
        ModuleUpgradeOption zpm = tierGroup.options()
            .stream()
            .filter(
                option -> option.id()
                    .equals(ModuleTier.ZPM.name()))
            .findFirst()
            .orElseThrow();

        assertFalse(ev.enabled());
        assertTrue(zpm.enabled());
        assertTrue(zpm.selected());
    }

    @Test
    void minerFocusOptionsExposeNoneWhenFocusIsInstalled() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.minerFocus(MinerFocusTier.NONE);

        ModuleUpgradeGroup group = ModuleUpgradeUiModel.groups(module, selection)
            .stream()
            .filter(
                candidate -> candidate.id()
                    .equals(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER))
            .findFirst()
            .orElseThrow();

        ModuleUpgradeOption none = group.options()
            .stream()
            .filter(
                option -> option.id()
                    .equals(MinerFocusTier.NONE.name()))
            .findFirst()
            .orElseThrow();

        assertTrue(none.enabled());
        assertTrue(none.selected());
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.NONE));
    }

    @Test
    void minerFocusOptionsExposeDisabledNoneWhenFocusIsNotInstalled() {
        ModuleInstance module = minerModule();
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.minerFocus(MinerFocusTier.I);

        ModuleUpgradeGroup group = ModuleUpgradeUiModel.groups(module, selection)
            .stream()
            .filter(
                candidate -> candidate.id()
                    .equals(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER))
            .findFirst()
            .orElseThrow();

        ModuleUpgradeOption none = group.options()
            .stream()
            .filter(
                option -> option.id()
                    .equals(MinerFocusTier.NONE.name()))
            .findFirst()
            .orElseThrow();

        assertFalse(none.enabled());
        assertFalse(none.selected());
    }

    @Test
    void minerFocusCanBeChangedFromNoneToAnyTier() {
        ModuleInstance module = minerModule();

        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.I));
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.II));
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.III));
    }

    @Test
    void minerFocusCanBeChangedToAnyOtherTier() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.II, "ore:iron", 1200);

        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.NONE));
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.I));
        assertFalse(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.II));
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.III));
    }

    @Test
    void minerFocusOptionsDisableOnlyCurrentTier() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.minerFocus(MinerFocusTier.III);

        ModuleUpgradeGroup group = ModuleUpgradeUiModel.groups(module, selection)
            .stream()
            .filter(
                candidate -> candidate.id()
                    .equals(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER))
            .findFirst()
            .orElseThrow();

        assertTrue(
            group.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(MinerFocusTier.NONE.name()))
                .findFirst()
                .orElseThrow()
                .enabled());
        assertFalse(
            group.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(MinerFocusTier.I.name()))
                .findFirst()
                .orElseThrow()
                .enabled());
        assertTrue(
            group.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(MinerFocusTier.II.name()))
                .findFirst()
                .orElseThrow()
                .enabled());
        assertTrue(
            group.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(MinerFocusTier.III.name()))
                .findFirst()
                .orElseThrow()
                .enabled());
    }

    private static ModuleInstance hammerModule() {
        return FacilityModuleKind.HAMMER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.IV);
    }

    private static ModuleInstance minerModule() {
        return FacilityModuleKind.MINER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
    }
}
