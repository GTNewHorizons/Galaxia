package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanContext;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidKnowledgePersistenceAdapterTest {

    private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @BeforeAll
    static void initRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearKnowledge() {
        AsteroidFieldKnowledgeStore.global()
            .clear();
    }

    @Test
    void discoveredAsteroidKnowledgeSurvivesSaveAndLoad(@TempDir Path tempDir) {
        AsteroidFieldProfile profile = GalaxiaCelestialAPI.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        AsteroidFieldKnowledgeStore store = AsteroidFieldKnowledgeStore.global();
        AsteroidFieldKnowledge knowledge = store.getOrCreate(TEAM_ID, CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldScanContext scanContext = new AsteroidFieldScanContext(
            node -> true,
            Comparator.comparingInt(AsteroidFieldNode::index));

        CelestialDiscoveryWork work = knowledge.nextDiscoveryWork(scanContext)
            .orElseThrow();
        while (work.step() == CelestialDiscoveryStep.DETECTION) {
            knowledge.revealDiscovery(work, scanContext);
            work = knowledge.nextDiscoveryWork(scanContext)
                .orElseThrow();
        }
        assertEquals(CelestialDiscoveryStep.SIGNATURE, work.step());
        knowledge.revealDiscovery(work, scanContext);
        AsteroidFieldKnowledgeSnapshot expected = knowledge.snapshot(CelestialObjectId.FROZEN_BELT);

        Path file = tempDir.resolve("_asteroids.json");
        AsteroidKnowledgePersistenceAdapter adapter = new AsteroidKnowledgePersistenceAdapter();
        Gson gson = new Gson();
        adapter.save(file.toFile(), gson);
        store.clear();
        adapter.load(file.toFile(), gson);

        assertEquals(
            expected,
            store.get(TEAM_ID, CelestialObjectId.FROZEN_BELT)
                .orElseThrow()
                .snapshot(CelestialObjectId.FROZEN_BELT));
    }

    @Test
    void unknownPersistedAsteroidFieldFailsLoudly(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("_asteroids.json");
        Files.writeString(
            file,
            "{\"teams\":[{\"teamId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"fields\":[{\"beltId\":\"MARS\",\"entries\":[]}]}]}");

        assertThrows(
            IllegalStateException.class,
            () -> new AsteroidKnowledgePersistenceAdapter().load(file.toFile(), new Gson()));
    }
}
