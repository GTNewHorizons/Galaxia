package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWorkerContribution;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class SatelliteDiscoveryWorkerSourceTest {

    private static final UUID TEAM = new UUID(41L, 42L);
    private static final CelestialObjectKey ANCHOR = CelestialObjectKey
        .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0));

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearAssets() {
        CelestialAssetStore.clear();
    }

    @Test
    void prospectingSatellitesAtOneAnchorBecomeOneGenericWorkerContribution() {
        registerProspectingSatellite();
        registerProspectingSatellite();

        List<CelestialDiscoveryWorkerContribution> contributions = SatelliteDiscoveryWorkerSource
            .prospectingWorkers(anchor -> ANCHOR.equals(anchor) ? OptionalLong.of(7L) : OptionalLong.empty());

        assertEquals(
            List.of(
                new CelestialDiscoveryWorkerContribution(
                    TEAM,
                    SatelliteDiscoveryWorkerSource.prospectingScope(ANCHOR, 7L),
                    CelestialDiscoveryCapability.PROSPECTING,
                    2,
                    SatelliteDiscoveryWorkerSource.PROSPECTING_EFFECT_PER_WORKER)),
            contributions);
    }

    /** Bug regression: an unfinished or disabled satellite cannot perform prospecting scans. */
    @Test
    void prospectingRequiresAnOperationalSatellite() {
        CelestialAsset satellite = CelestialAsset.create(
            ANCHOR,
            CelestialAsset.Kind.SATELLITE,
            Buildable.Status.CONSTRUCTION_SITE,
            SatelliteKind.PROSPECTING);
        CelestialAssetStore.registerAsset(TEAM, satellite);

        assertTrue(
            SatelliteDiscoveryWorkerSource
                .prospectingWorkers(anchor -> ANCHOR.equals(anchor) ? OptionalLong.of(7L) : OptionalLong.empty())
                .isEmpty());

        satellite.updateStatus(Buildable.Status.OPERATIONAL);
        assertEquals(
            1,
            SatelliteDiscoveryWorkerSource
                .prospectingWorkers(anchor -> ANCHOR.equals(anchor) ? OptionalLong.of(7L) : OptionalLong.empty())
                .size());

        satellite.updateStatus(Buildable.Status.DISABLED);
        assertTrue(
            SatelliteDiscoveryWorkerSource
                .prospectingWorkers(anchor -> ANCHOR.equals(anchor) ? OptionalLong.of(7L) : OptionalLong.empty())
                .isEmpty());
    }

    private static void registerProspectingSatellite() {
        CelestialAssetStore.registerAsset(
            TEAM,
            CelestialAsset.create(
                ANCHOR,
                CelestialAsset.Kind.SATELLITE,
                Buildable.Status.OPERATIONAL,
                SatelliteKind.PROSPECTING));
    }
}
