package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot.CelestialDiscoveryStep;

final class CelestialDiscoveryPersistenceAdapterTest {

    @Test
    void registeredPlanetScanRoundTripsWithoutAsteroidKnowledge(@TempDir Path tempDir) {
        UUID teamId = UUID.randomUUID();
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialDiscoveryScanSnapshot expected = new CelestialDiscoveryScanSnapshot(
            teamId,
            mars,
            2.5,
            7L,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            CelestialObjectKey.registered(CelestialObjectId.MOON),
            CelestialDiscoveryStep.DETECTION,
            3L);
        CelestialDiscoveryScanService saved = new CelestialDiscoveryScanService(scope -> null);
        saved.restore(teamId, List.of(expected));
        Path file = tempDir.resolve("_discovery.json");
        new CelestialDiscoveryPersistenceAdapter(saved).save(file.toFile(), new Gson());

        CelestialDiscoveryScanService loaded = new CelestialDiscoveryScanService(scope -> null);
        new CelestialDiscoveryPersistenceAdapter(loaded).load(file.toFile(), new Gson());

        assertEquals(List.of(expected), loaded.snapshots(teamId));
    }
}
