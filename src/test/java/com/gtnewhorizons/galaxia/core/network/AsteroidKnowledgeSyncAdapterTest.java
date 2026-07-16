package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AuthoredAsteroidDefinition;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class AsteroidKnowledgeSyncAdapterTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void initRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearState() {
        AsteroidFieldKnowledgeStore.global()
            .clear();
        AsteroidFieldClientKnowledgeState.clear();
    }

    @Test
    void asteroidKnowledgeRoundTripsThroughDedicatedSyncSection() {
        AsteroidFieldProfile profile = profile();
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromGenerated(CelestialObjectId.FROZEN_BELT, profile);
        int savedIndex = catalog.nodes()
            .get(0)
            .index();
        AsteroidFieldKnowledgeSnapshot expected = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    savedIndex,
                    DiscoveryState.DISCOVERED,
                    CelestialResourceKnowledgeState.PROFILE)),
            catalog.snapshots());
        AsteroidFieldKnowledgeStore.global()
            .restore(TEAM, List.of(expected), id -> java.util.Optional.of(profile));
        AsteroidKnowledgeSyncAdapter adapter = new AsteroidKnowledgeSyncAdapter();
        ByteBuf buf = Unpooled.buffer();
        adapter.write(buf, TEAM);

        adapter.read(buf)
            .applyClient();

        assertEquals(
            AsteroidFieldKnowledgeStore.global()
                .snapshots(TEAM),
            AsteroidFieldClientKnowledgeState.snapshots());
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .sizeCounts(0, 0, 4)
            .radialBand(1, 2)
            .placementConnectionRadius(1)
            .oreProfile(new AsteroidOreProfile("test", List.of()))
            .authoredAsteroid(
                new AuthoredAsteroidDefinition(0, AsteroidNodeKind.LORE, "Anchor", DiscoveryState.DISCOVERED))
            .build();
    }
}
