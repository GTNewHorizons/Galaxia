package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalSceneSatelliteMarkerTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void satelliteMarkersCollapseMultipleAssetsOfTheSameKind() {
        List<CelestialAsset> assets = List.of(
            satellite(SatelliteKind.COMMUNICATION),
            satellite(SatelliteKind.COMMUNICATION),
            satellite(SatelliteKind.PROSPECTING));

        assertEquals(
            Set.of(SatelliteKind.COMMUNICATION, SatelliteKind.PROSPECTING),
            OrbitalScene.visibleSatelliteMarkerAlphas(assets)
                .keySet());
    }

    @Test
    void satelliteMarkerCountIgnoresNonSatelliteAssets() {
        List<CelestialAsset> assets = List.of(
            CelestialAsset.create(
                CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                CelestialAsset.Kind.AUTOMATED_STATION,
                Buildable.Status.OPERATIONAL),
            satellite(SatelliteKind.COMMUNICATION));

        assertEquals(
            Set.of(SatelliteKind.COMMUNICATION),
            OrbitalScene.visibleSatelliteMarkerAlphas(assets)
                .keySet());
    }

    private static CelestialAsset satellite(SatelliteKind kind) {
        return CelestialAsset.create(
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            CelestialAsset.Kind.SATELLITE,
            Buildable.Status.OPERATIONAL,
            kind);
    }
}
