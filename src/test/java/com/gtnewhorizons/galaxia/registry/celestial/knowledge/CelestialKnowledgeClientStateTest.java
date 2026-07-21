package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialKnowledgeClientStateTest {

    @AfterEach
    void clearState() {
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientKnowledgeState.clear();
    }

    @Test
    void exposesSyncedDiscoveryThroughGenericClientView() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 2000));
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            2000,
                            DiscoveryState.DISCOVERED,
                            CelestialResourceKnowledgeState.SIGNATURE)),
                    List.of())));

        CelestialDiscoveryView view = CelestialKnowledgeClientState.discoveryView();

        assertEquals(Optional.of(DiscoveryState.DISCOVERED), view.discoveryState(asteroidKey));
        assertTrue(view.isVisible(asteroidKey, DiscoveryState.HIDDEN));
        assertEquals(
            Optional.of(CelestialResourceKnowledgeState.SIGNATURE),
            AsteroidFieldClientKnowledgeState.oreKnowledge(asteroidKey));
        assertEquals(Optional.empty(), view.discoveryState(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void hiddenSyncedStateStaysHiddenEvenWhenInitialStateWouldBeVisible() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 2001));
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            2001,
                            DiscoveryState.HIDDEN,
                            CelestialResourceKnowledgeState.UNKNOWN)),
                    List.of())));

        CelestialDiscoveryView view = CelestialKnowledgeClientState.discoveryView();

        assertEquals(Optional.of(DiscoveryState.HIDDEN), view.discoveryState(asteroidKey));
        assertFalse(view.isVisible(asteroidKey, DiscoveryState.DISCOVERED));
    }
}
