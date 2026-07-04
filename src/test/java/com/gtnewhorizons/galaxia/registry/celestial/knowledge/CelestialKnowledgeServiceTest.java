package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialKnowledgeServiceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000777");

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void clearKnowledge() {
        CelestialKnowledgeService.clear();
    }

    @Test
    void registeredBodiesDefaultToDiscoveredThroughGenericKeyLookup() {
        assertEquals(
            DiscoveryState.DISCOVERED,
            CelestialKnowledgeService.discoveryState(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void generatedAsteroidsUseInitialFieldKnowledgeThroughGenericKeyLookup() {
        AsteroidFieldProfile profile = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .map(CelestialObject::properties)
            .map(properties -> properties.asteroidFieldProfile())
            .orElseThrow();
        AsteroidFieldNode hidden = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
            .findFirst()
            .orElseThrow();

        assertEquals(
            DiscoveryState.HIDDEN,
            CelestialKnowledgeService.discoveryState(TEAM, CelestialObjectKey.minorBody(hidden.id())));
    }

    @Test
    void asteroidKnowledgeMutationLivesBehindAsteroidServiceWhileGenericLookupStaysShared() {
        AsteroidFieldProfile profile = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .map(CelestialObject::properties)
            .map(properties -> properties.asteroidFieldProfile())
            .orElseThrow();
        AsteroidFieldNode hidden = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
            .findFirst()
            .orElseThrow();

        AsteroidFieldKnowledgeService.knowledge(TEAM, CelestialObjectId.FROZEN_BELT, profile)
            .detect(hidden.id());

        assertEquals(
            DiscoveryState.DISCOVERED,
            CelestialKnowledgeService.discoveryState(TEAM, CelestialObjectKey.minorBody(hidden.id())));
    }

    @Test
    void genericLookupFailsLoudlyForUnresolvableKeys() {
        CelestialObjectKey missingAsteroid = CelestialObjectKey.minorBody(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN + 999999));

        assertThrows(IllegalArgumentException.class, () -> CelestialKnowledgeService.discoveryState(TEAM, null));
        assertThrows(
            IllegalStateException.class,
            () -> CelestialKnowledgeService.discoveryState(TEAM, missingAsteroid));
    }
}
