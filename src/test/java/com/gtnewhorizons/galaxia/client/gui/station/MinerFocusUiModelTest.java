package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class MinerFocusUiModelTest {

    @BeforeAll
    static void initRegistry() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void tierCanBePlannedOnlyWhenTargetDiffersAndNoOperationIsActive() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);

        assertFalse(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.I));
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.II));

        module.setOperation(
            ModuleOperationState.waiting(
                new ModuleOperationPlan(
                    new MinerFocusOperation(ModuleTier.EV, MinerFocusTier.II.name(), "ore:iron"),
                    2,
                    java.util.Map.of(),
                    false)));

        assertFalse(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.II));
    }

    @Test
    void oreCanBeSetOnlyAfterFocusTierIsInstalled() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();

        assertFalse(MinerFocusUiModel.canSetOre(module, "ore:iron"));
        assertFalse(MinerFocusUiModel.canShowOreFocus(module));

        miner.setFocus(MinerFocusTier.I, null, 0);

        assertTrue(MinerFocusUiModel.canSetOre(module, "ore:iron"));
        assertTrue(MinerFocusUiModel.canShowOreFocus(module));
    }

    @Test
    void defaultUpgradeTargetAdvancesUntilMaxFocusTier() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();

        assertEquals(MinerFocusTier.I, MinerFocusUiModel.defaultUpgradeTarget(module));

        miner.setFocus(MinerFocusTier.I, null, 0);
        assertEquals(MinerFocusTier.II, MinerFocusUiModel.defaultUpgradeTarget(module));

        miner.setFocus(MinerFocusTier.II, null, 0);
        assertEquals(MinerFocusTier.III, MinerFocusUiModel.defaultUpgradeTarget(module));

        miner.setFocus(MinerFocusTier.III, null, 0);
        assertEquals(MinerFocusTier.III, MinerFocusUiModel.defaultUpgradeTarget(module));
        assertTrue(MinerFocusUiModel.canPlanTier(module, MinerFocusTier.II));
    }

    @Test
    void oreCanBeSetWhileModuleHasBuildInProgress() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, null, 0);
        module.setOperation(
            ModuleOperationState.waiting(
                new ModuleOperationPlan(
                    new MinerFocusOperation(ModuleTier.EV, MinerFocusTier.II.name(), null),
                    2,
                    java.util.Map.of(),
                    false)));

        assertTrue(MinerFocusUiModel.canSetOre(module, "ore:iron"));
    }

    @Test
    void selectedOreClickClearsTarget() {
        ModuleInstance module = minerModule();
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);

        assertNull(MinerFocusUiModel.oreTargetForClick(module, "ore:iron"));
        assertEquals("ore:gold", MinerFocusUiModel.oreTargetForClick(module, "ore:gold"));
    }

    private static ModuleInstance minerModule() {
        return FacilityModuleKind.MINER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
    }
}
