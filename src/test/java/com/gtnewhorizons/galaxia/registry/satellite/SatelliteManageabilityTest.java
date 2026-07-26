package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class SatelliteManageabilityTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void operationalSatellitesAreNotManagedAsStationAssets() {
        CelestialAsset satellite = CelestialAsset.create(
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            CelestialAsset.Kind.SATELLITE,
            Buildable.Status.OPERATIONAL,
            SatelliteKind.COMMUNICATION);

        assertFalse(satellite.isManageable());
    }
}
