package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

final class FacilityModuleKindTest {

    @Test
    void minerIsOnlyAllowedOnAutomatedOutposts() {
        assertTrue(FacilityModuleKind.MINER.isAllowedOn(CelestialAsset.Kind.AUTOMATED_OUTPOST));
        assertFalse(FacilityModuleKind.MINER.isAllowedOn(CelestialAsset.Kind.AUTOMATED_STATION));
        assertFalse(FacilityModuleKind.MINER.isAllowedOn(CelestialAsset.Kind.STATION));
    }

    @Test
    void nonMiningModulesAreAllowedOnBothAutomatedFacilityKinds() {
        assertTrue(FacilityModuleKind.POWER.isAllowedOn(CelestialAsset.Kind.AUTOMATED_OUTPOST));
        assertTrue(FacilityModuleKind.POWER.isAllowedOn(CelestialAsset.Kind.AUTOMATED_STATION));
        assertFalse(FacilityModuleKind.POWER.isAllowedOn(CelestialAsset.Kind.STATION));
    }

    @Test
    void productionKindsIncludeHammerMinerAndRecipeModules() {
        assertTrue(FacilityModuleKind.HAMMER.isProductionModule());
        assertTrue(FacilityModuleKind.MINER.isProductionModule());
        assertTrue(FacilityModuleKind.MACERATOR.isProductionModule());
        assertFalse(FacilityModuleKind.POWER.isProductionModule());
    }
}
