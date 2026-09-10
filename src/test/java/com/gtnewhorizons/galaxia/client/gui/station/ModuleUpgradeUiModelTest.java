package com.gtnewhorizons.galaxia.client.gui.station;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
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
    void minerUpgradePreviewMatchesMaterialsRequiredByServerPlan() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = minerModule();
        addModule(facility, module);
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.miner(ModuleTier.IV, MinerFocusTier.I);
        Map<ItemStackWrapper, Long> displayed = ModuleUpgradeUiModel.upgradeMaterials(module, selection);
        assertEquals(
            FacilityCommand.Status.CHANGED,
            facility.applyCommand(
                new FacilityCommand.PlanMinerFocusUpgrade(facility.assetId, module.id, ModuleTier.IV, MinerFocusTier.I),
                FacilityCommand.Authority.NONE)
                .status());
        assertEquals(
            module.operationOrNull()
                .plan()
                .materialCost(),
            displayed);
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
    void buildTierFallsBackWhenHammerPendingTierWasCleared() {
        assertEquals(
            ModuleTier.LuV,
            ModuleUpgradeUiModel.normalizeBuildTier(FacilityModuleKind.HAMMER, ModuleTier.NONE, HammerVariant.BIG));
    }

    @Test
    void buildTierKeepsValidHammerTier() {
        assertEquals(
            ModuleTier.UV,
            ModuleUpgradeUiModel.normalizeBuildTier(FacilityModuleKind.HAMMER, ModuleTier.UV, HammerVariant.BIG));
    }

    @Test
    void buildTierFallsBackToDefaultForInvalidNonHammerTier() {
        assertEquals(
            ModuleTier.HV,
            ModuleUpgradeUiModel.normalizeBuildTier(FacilityModuleKind.MACERATOR, ModuleTier.NONE, HammerVariant.BASE));
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
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.miner(ModuleTier.EV, MinerFocusTier.NONE);

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
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.miner(ModuleTier.EV, MinerFocusTier.I);

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
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.miner(ModuleTier.EV, MinerFocusTier.III);

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

    @Test
    void minerUpgradeOptionsExposeModuleTierAndFocusTier() {
        ModuleInstance module = minerModule();
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.miner(ModuleTier.IV, MinerFocusTier.II);

        List<ModuleUpgradeGroup> groups = ModuleUpgradeUiModel.groups(module, selection);

        ModuleUpgradeGroup tierGroup = groups.stream()
            .filter(
                group -> group.id()
                    .equals(ModuleUpgradeUiModel.GROUP_MINER_TIER))
            .findFirst()
            .orElseThrow();
        ModuleUpgradeGroup focusGroup = groups.stream()
            .filter(
                group -> group.id()
                    .equals(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER))
            .findFirst()
            .orElseThrow();

        assertEquals(
            List.of(ModuleTier.EV.name(), ModuleTier.IV.name(), ModuleTier.LuV.name()),
            tierGroup.options()
                .stream()
                .map(ModuleUpgradeOption::id)
                .toList());
        assertTrue(
            tierGroup.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(ModuleTier.EV.name()))
                .findFirst()
                .orElseThrow()
                .enabled());
        assertTrue(
            tierGroup.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(ModuleTier.IV.name()))
                .findFirst()
                .orElseThrow()
                .selected());
        assertTrue(
            focusGroup.options()
                .stream()
                .filter(
                    option -> option.id()
                        .equals(MinerFocusTier.II.name()))
                .findFirst()
                .orElseThrow()
                .selected());
    }

    @Test
    void minerTierSelectionPreservesFocusSelection() {
        ModuleInstance module = minerModule();
        ModuleUpgradeSelection selection = ModuleUpgradeSelection.miner(ModuleTier.EV, MinerFocusTier.III);

        ModuleUpgradeSelection normalized = ModuleUpgradeUiModel
            .selectOption(module, selection, ModuleUpgradeUiModel.GROUP_MINER_TIER, ModuleTier.IV.name());

        assertEquals(ModuleTier.IV.name(), normalized.get(ModuleUpgradeUiModel.GROUP_MINER_TIER));
        assertEquals(MinerFocusTier.III.name(), normalized.get(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER));
    }

    @Test
    void confirmedUpgradeTargetsUseStableModuleIds() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance source = FacilityModuleKind.HAMMER
            .create(StationTileCoord.of(0, 0), ModuleShape.SINGLE, ModuleTier.EV);
        ModuleInstance target = FacilityModuleKind.HAMMER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        addModule(facility, source);
        addModule(facility, target);
        facility.stationLayout()
            .place(source);
        facility.stationLayout()
            .place(target);

        assertEquals(
            List.of(target.id),
            ModuleUpgradeUiModel.confirmedTargets(
                facility,
                source,
                ModuleTier.IV,
                HammerVariant.BASE,
                List.of(target.anchor(), target.anchor())));
    }

    @Test
    void completedOperationWithUnsettledMaterialsCannotBeSelectedForAnotherUpgrade() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = hammerModule();
        addModule(facility, module);
        facility.stationLayout()
            .place(module);
        var materials = Map.of(new ItemStackWrapper(Items.iron_ingot, 0, null), 1L);
        var plan = new ModuleOperationPlan(
            new IModuleOperation.Hammer(ModuleTier.IV, HammerVariant.BASE),
            1,
            materials,
            true);
        module.setOperation(ModuleOperationState.restore(plan, ModuleOperationPhase.COMPLETE, 1, materials, Map.of()));

        assertFalse(
            ModuleUpgradeUiModel
                .isCompatibleTarget(facility, module, ModuleTier.LuV, HammerVariant.BASE, module.anchor()));
    }

    private static ModuleInstance hammerModule() {
        return FacilityModuleKind.HAMMER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.IV);
    }

    private static ModuleInstance minerModule() {
        return FacilityModuleKind.MINER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
    }
}
