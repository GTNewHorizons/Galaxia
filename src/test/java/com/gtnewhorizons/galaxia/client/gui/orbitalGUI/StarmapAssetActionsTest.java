package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StarmapAssetActionsTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearClientKnowledge() {
        CelestialKnowledgeClientState.clear();
    }

    @Test
    void asteroidAssetActionsAllowOutpostButRejectAutomatedStationCreation() {
        StarmapAssetActions.OrbitalAssetUiState state = new StarmapAssetActions.OrbitalAssetUiState();
        StarmapAssetActions.OrbitalAssetActionController controller = new StarmapAssetActions.OrbitalAssetActionController(
            new TestCallbacks(),
            new TestStarmapView());
        CelestialObject asteroid = CelestialRegistry
            .get(
                CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN)))
            .orElseThrow();
        setDiscovery(asteroid, DiscoveryState.DISCOVERED);

        controller.triggerAssetCreation(state, asteroid, CelestialAsset.Kind.AUTOMATED_STATION, false);

        assertNull(state.pendingAssetCreation);

        controller.triggerAssetCreation(state, asteroid, CelestialAsset.Kind.AUTOMATED_OUTPOST, false);

        assertNotNull(state.pendingAssetCreation);
        assertEquals(CelestialAsset.Kind.AUTOMATED_OUTPOST, state.pendingAssetCreation.kind());
    }

    @Test
    void hiddenAsteroidAssetActionsDoNotQueueAssetCreation() {
        StarmapAssetActions.OrbitalAssetUiState state = new StarmapAssetActions.OrbitalAssetUiState();
        StarmapAssetActions.OrbitalAssetActionController controller = new StarmapAssetActions.OrbitalAssetActionController(
            new TestCallbacks(),
            new TestStarmapView());
        CelestialObject hiddenAsteroid = hiddenAsteroid();
        setDiscovery(hiddenAsteroid, DiscoveryState.HIDDEN);

        controller.triggerAssetCreation(state, hiddenAsteroid, CelestialAsset.Kind.AUTOMATED_OUTPOST, false);

        assertNull(state.pendingAssetCreation);
    }

    @Test
    void registeredBodyUsesEffectiveDiscoveredDefaultWithoutExplicitSync() {
        StarmapAssetActions.OrbitalAssetUiState state = new StarmapAssetActions.OrbitalAssetUiState();
        StarmapAssetActions.OrbitalAssetActionController controller = new StarmapAssetActions.OrbitalAssetActionController(
            new TestCallbacks(),
            new TestStarmapView());
        CelestialObject mars = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();

        controller.triggerAssetCreation(state, mars, CelestialAsset.Kind.AUTOMATED_OUTPOST, false);

        assertNotNull(state.pendingAssetCreation);
    }

    @Test
    void pendingSatelliteDeletionBlocksOtherModalActions() {
        StarmapAssetActions.OrbitalAssetUiState state = new StarmapAssetActions.OrbitalAssetUiState();
        state.pendingSatelliteDeletion = new PendingSatelliteDeletion(SatelliteKind.COMMUNICATION, 1);

        assertTrue(state.hasBlockingModal());
    }

    private static void setDiscovery(CelestialObject body, DiscoveryState detectionState) {
        CelestialKnowledgeClientState.apply(
            Map.of(body.key(), CelestialKnowledgeFacts.of(detectionState, CelestialResourceKnowledgeState.UNKNOWN)));
    }

    private static CelestialObject hiddenAsteroid() {
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        return AsteroidFieldResolver.resolveAll(
            CelestialObjectId.FROZEN_BELT,
            belt.properties()
                .asteroidFieldProfile())
            .stream()
            .filter(node -> node.initialDetectionState() == DiscoveryState.HIDDEN)
            .findFirst()
            .flatMap(node -> CelestialRegistry.get(CelestialObjectKey.minorBody(node.id())))
            .orElseThrow();
    }

    private static final class TestCallbacks implements StarmapAssetActions.OrbitalAssetActionController.Callbacks {

        @Override
        public void showActionStatus(String message) {}

        @Override
        public void beginRenameInput(String currentText) {}

        @Override
        public void endRenameInput() {}

        @Override
        public String getRenameInput() {
            return "";
        }

        @Override
        public void createResourceTransfer(CelestialObject sourceBody, CelestialAsset sourceAsset,
            CelestialAsset target) {}
    }
}
