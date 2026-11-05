package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialAssetInventoryCapabilityTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void onlyPhysicalStationExposesDistributedInventory() {
        CelestialAsset station = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.MOON,
            Buildable.Status.OPERATIONAL);
        CelestialAsset facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAsset satellite = new Satellite(
            CelestialAsset.ID.create(),
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            Buildable.Status.OPERATIONAL,
            SatelliteKind.COMMUNICATION);

        assertTrue(IDistributedInventory.class.isInstance(station));
        assertFalse(IDistributedInventory.class.isInstance(facility));
        assertFalse(IDistributedInventory.class.isInstance(satellite));
    }
}
