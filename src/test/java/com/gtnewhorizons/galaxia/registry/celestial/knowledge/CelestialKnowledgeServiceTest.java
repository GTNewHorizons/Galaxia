package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialKnowledgeServiceTest {

    private static final UUID TEAM_A = UUID.fromString("00000000-0000-0000-0000-000000000777");
    private static final UUID TEAM_B = UUID.fromString("00000000-0000-0000-0000-000000000778");

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void clearFacts() {
        CelestialKnowledgeService.clearFacts();
        CelestialKnowledgeService.resetDiscoveryDomainsForTesting();
    }

    @Test
    void registeredBodiesDefaultToDiscoveredUnknown() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeService.discoveryState(TEAM_A, mars));
        assertEquals(
            CelestialResourceKnowledgeState.UNKNOWN,
            CelestialKnowledgeService.resourceKnowledge(TEAM_A, mars));
    }

    @Test
    void minorBodiesUseAsteroidInitialFactsWithoutStoredOverride() {
        AsteroidFieldNode hidden = firstMediumHidden();
        CelestialObjectKey key = CelestialObjectKey.minorBody(hidden.id());
        assertEquals(DiscoveryState.HIDDEN, CelestialKnowledgeService.discoveryState(TEAM_A, key));
        assertEquals(CelestialResourceKnowledgeState.UNKNOWN, CelestialKnowledgeService.resourceKnowledge(TEAM_A, key));
    }

    @Test
    void majorAndMinorShareSameFactsApi() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey asteroid = CelestialObjectKey.minorBody(firstMediumHidden().id());
        CelestialKnowledgeService.putFacts(TEAM_A, mars, CelestialKnowledgeFacts.hidden());
        CelestialKnowledgeService.putFacts(TEAM_A, asteroid, CelestialKnowledgeFacts.discoveredUnknown());
        assertEquals(DiscoveryState.HIDDEN, CelestialKnowledgeService.discoveryState(TEAM_A, mars));
        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeService.discoveryState(TEAM_A, asteroid));
    }

    @Test
    void explicitFactOverridesDefinitionDefault() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeService.discoveryState(TEAM_A, mars));
        CelestialKnowledgeService.putFacts(TEAM_A, mars, CelestialKnowledgeFacts.hidden());
        assertEquals(DiscoveryState.HIDDEN, CelestialKnowledgeService.discoveryState(TEAM_A, mars));
    }

    @Test
    void teamsAreIsolated() {
        CelestialObjectKey asteroid = CelestialObjectKey.minorBody(firstMediumHidden().id());
        CelestialKnowledgeService.putFacts(TEAM_A, asteroid, CelestialKnowledgeFacts.discoveredUnknown());
        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeService.discoveryState(TEAM_A, asteroid));
        assertEquals(DiscoveryState.HIDDEN, CelestialKnowledgeService.discoveryState(TEAM_B, asteroid));
    }

    @Test
    void unknownKeyFailsLoudly() {
        CelestialObjectKey missing = CelestialObjectKey.minorBody(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN + 999999));
        assertThrows(IllegalArgumentException.class, () -> CelestialKnowledgeService.discoveryState(TEAM_A, null));
        assertThrows(IllegalStateException.class, () -> CelestialKnowledgeService.discoveryState(TEAM_A, missing));
        assertThrows(
            IllegalStateException.class,
            () -> CelestialKnowledgeService.putFacts(TEAM_A, missing, CelestialKnowledgeFacts.discoveredUnknown()));
    }

    @Test
    void hiddenFactsRejectNonUnknownResourceKnowledge() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialKnowledgeFacts.of(DiscoveryState.HIDDEN, CelestialResourceKnowledgeState.SIGNATURE));
    }

    @Test
    void snapshotRestoreRoundTripsExplicitFactsOnly() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialKnowledgeService.putFacts(
            TEAM_A,
            mars,
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
        var snapshot = CelestialKnowledgeService.snapshot(TEAM_A);
        CelestialKnowledgeService.clearFacts();
        assertNotEquals(
            CelestialResourceKnowledgeState.PROFILE,
            CelestialKnowledgeService.resourceKnowledge(TEAM_A, mars));
        CelestialKnowledgeService.restore(TEAM_A, snapshot);
        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            CelestialKnowledgeService.resourceKnowledge(TEAM_A, mars));
    }

    private static AsteroidFieldNode firstMediumHidden() {
        AsteroidFieldProfile profile = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .map(CelestialObject::properties)
            .map(properties -> properties.asteroidFieldProfile())
            .orElseThrow();
        return AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
            .filter(node -> node.initialDetectionState() == DiscoveryState.HIDDEN)
            .findFirst()
            .orElseThrow();
    }
}
