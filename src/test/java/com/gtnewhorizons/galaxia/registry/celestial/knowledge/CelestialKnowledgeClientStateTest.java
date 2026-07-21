package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialKnowledgeClientStateTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearState() {
        CelestialKnowledgeClientState.clear();
    }

    @Test
    void exposesSyncedDiscoveryThroughGenericClientView() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 2000));
        CelestialKnowledgeClientState.apply(
            Map.of(
                asteroidKey,
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN)));

        CelestialDiscoveryView view = CelestialKnowledgeClientState.discoveryView();

        assertEquals(Optional.of(DiscoveryState.DISCOVERED), view.discoveryState(asteroidKey));
        assertTrue(view.isVisible(asteroidKey, DiscoveryState.HIDDEN));
        assertEquals(
            Optional.of(CelestialResourceKnowledgeState.UNKNOWN),
            CelestialKnowledgeClientState.resourceKnowledge(asteroidKey));
        assertEquals(Optional.empty(), view.discoveryState(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void hiddenSyncedStateStaysHiddenEvenWhenInitialStateWouldBeVisible() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 2001));
        CelestialKnowledgeClientState.apply(Map.of(asteroidKey, CelestialKnowledgeFacts.hidden()));

        CelestialDiscoveryView view = CelestialKnowledgeClientState.discoveryView();

        assertEquals(Optional.of(DiscoveryState.HIDDEN), view.discoveryState(asteroidKey));
        assertFalse(view.isVisible(asteroidKey, DiscoveryState.DISCOVERED));
    }

    @Test
    void effectiveDiscoveryFallsBackToRegisteredDefault() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeClientState.effectiveDiscoveryState(mars));
    }
}
