package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

final class ModuleMinerTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
    }

    @Test
    void minerBlacklistIsSparseAndValidated() {
        AutomatedFacility facility = createFacility();

        assertFalse(facility.isMinerOreBlacklisted("ore:iron"));

        facility.setMinerOreBlacklisted("ore:iron", true);
        assertTrue(facility.isMinerOreBlacklisted("ore:iron"));
        assertTrue(
            facility.minerBlacklistedOreKeys()
                .contains("ore:iron"));

        facility.setMinerOreBlacklisted("ore:iron", false);
        assertFalse(facility.isMinerOreBlacklisted("ore:iron"));
        assertFalse(
            facility.minerBlacklistedOreKeys()
                .contains("ore:iron"));
    }

    @Test
    void bulkBlacklistLoadCrashesOnMalformedOreKey() {
        AutomatedFacility facility = createFacility();

        org.junit.jupiter.api.Assertions
            .assertThrows(IllegalArgumentException.class, () -> facility.setMinerBlacklistedOreKeys(Set.of("")));
    }

    @Test
    void bulkBlacklistLoadReplacesSparseSet() {
        AutomatedFacility facility = createFacility();
        facility.setMinerOreBlacklisted("ore:iron", true);

        facility.setMinerBlacklistedOreKeys(Set.of("ore:copper"));

        assertFalse(facility.isMinerOreBlacklisted("ore:iron"));
        assertTrue(facility.isMinerOreBlacklisted("ore:copper"));
    }

    @Test
    void blacklistVoidsOreAfterRoll() {
        AutomatedFacility facility = createFacility();
        facility.setMinerOreBlacklisted("ore:iron", true);

        assertTrue(ModuleMiner.shouldVoidOre(facility, "ore:iron"));
        assertFalse(ModuleMiner.shouldVoidOre(facility, "ore:copper"));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
