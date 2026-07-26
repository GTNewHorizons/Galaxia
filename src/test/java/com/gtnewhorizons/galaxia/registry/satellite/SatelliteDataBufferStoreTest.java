package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class SatelliteDataBufferStoreTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000106");

    @Test
    void originDataKeyIsDistinctFromAnyDataKeyAndMatchesOriginDemandFirst() {
        SatelliteDataKey egoraProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteDataKey anyProspecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);

        assertNotEquals(egoraProspecting, anyProspecting);

        List<SatelliteDataKey> matches = SatelliteDataKey
            .matchingDemandKeys(egoraProspecting, List.of(anyProspecting, egoraProspecting));

        assertIterableEquals(List.of(egoraProspecting), matches);
    }

    @Test
    void anyDemandMatchesWhenNoConcreteOriginDemandExists() {
        SatelliteDataKey marsProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.MARS));
        SatelliteDataKey egoraProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteDataKey anyProspecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);

        List<SatelliteDataKey> matches = SatelliteDataKey
            .matchingDemandKeys(marsProspecting, List.of(anyProspecting, egoraProspecting));

        assertIterableEquals(List.of(anyProspecting), matches);
    }

    @Test
    void minorBodyOriginDemandMatchesOnlyTheSameMinorBody() {
        CelestialObjectKey asteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 42));
        CelestialObjectKey otherAsteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 43));
        SatelliteDataKey asteroidProspecting = SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, asteroid);
        SatelliteDataKey otherAsteroidProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, otherAsteroid);
        SatelliteDataKey anyProspecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);

        assertTrue(anyProspecting.matchesProduced(asteroidProspecting));
        assertTrue(
            SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, asteroid)
                .matchesProduced(asteroidProspecting));
        assertFalse(otherAsteroidProspecting.matchesProduced(asteroidProspecting));

        List<SatelliteDataKey> matches = SatelliteDataKey
            .matchingDemandKeys(asteroidProspecting, List.of(anyProspecting, otherAsteroidProspecting));

        assertIterableEquals(List.of(anyProspecting), matches);
    }

    @Test
    void finishProductionCanOverfillBufferAndBlocksOnlyThatKeyUntilDrained() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey egoraProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteDataKey anyProspecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);

        store.finishProduction(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            egoraProspecting,
            SatelliteBandwidthFormatter.kilobits(15L));

        assertEquals(
            SatelliteBandwidthFormatter.kilobits(15L),
            store.pendingDeciKb(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS), egoraProspecting));
        assertFalse(store.canStart(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS), egoraProspecting, 10L));
        assertTrue(store.canStart(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS), anyProspecting, 10L));

        store.drain(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            egoraProspecting,
            SatelliteBandwidthFormatter.kilobits(6L));

        assertTrue(store.canStart(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS), egoraProspecting, 10L));
    }

    @Test
    void bufferLimitScalesWithLocalBandwidthCapacity() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey key = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);

        store.finishProduction(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            key,
            SatelliteBandwidthFormatter.kilobits(15L));

        assertFalse(store.canStart(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS), key, 10L));
        assertTrue(store.canStart(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS), key, 20L));
    }
}
