package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

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
    void voidChanceIsSparseAndValidated() {
        AutomatedFacility facility = createFacility();

        assertEquals(0, facility.minerVoidChancePercent("ore:iron"));

        facility.setMinerVoidChancePercent("ore:iron", 75);
        assertEquals(75, facility.minerVoidChancePercent("ore:iron"));

        facility.setMinerVoidChancePercent("ore:iron", 0);
        assertEquals(0, facility.minerVoidChancePercent("ore:iron"));
        assertFalse(
            facility.minerVoidChances()
                .containsKey("ore:iron"));

        facility.setMinerVoidChancePercent("ore:iron", -1);
        assertEquals(0, facility.minerVoidChancePercent("ore:iron"));
        assertFalse(
            facility.minerVoidChances()
                .containsKey("ore:iron"));

        facility.setMinerVoidChancePercent("ore:iron", 101);
        assertEquals(100, facility.minerVoidChancePercent("ore:iron"));
    }

    @Test
    void bulkVoidChanceLoadCrashesOnMalformedPercent() {
        AutomatedFacility facility = createFacility();

        org.junit.jupiter.api.Assertions
            .assertThrows(IllegalArgumentException.class, () -> facility.setMinerVoidChances(Map.of("ore:iron", 101)));
    }

    @Test
    void voidChanceUsesPercentThreshold() {
        AutomatedFacility facility = createFacility();
        facility.setMinerVoidChancePercent("ore:iron", 50);

        assertTrue(ModuleMiner.shouldVoidOre(facility, "ore:iron", 0));
        assertTrue(ModuleMiner.shouldVoidOre(facility, "ore:iron", 49));
        assertFalse(ModuleMiner.shouldVoidOre(facility, "ore:iron", 50));
        assertFalse(ModuleMiner.shouldVoidOre(facility, "ore:iron", 99));
        assertFalse(ModuleMiner.shouldVoidOre(facility, "ore:copper", 0));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
