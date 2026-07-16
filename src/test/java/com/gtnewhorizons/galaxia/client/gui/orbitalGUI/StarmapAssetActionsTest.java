package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StarmapAssetActionsTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearClientKnowledge() {
        AsteroidFieldClientKnowledgeState.clear();
    }

    @Test
    void asteroidAssetActionsAllowOutpostButRejectAutomatedStationCreation() {
        StarmapAssetActions.OrbitalAssetUiState state = new StarmapAssetActions.OrbitalAssetUiState();
        StarmapAssetActions.OrbitalAssetActionController controller = new StarmapAssetActions.OrbitalAssetActionController(
            new StarmapAssetActions.OrbitalAssetSupport(),
            new TestCallbacks());
        CelestialObject asteroid = CelestialRegistry
            .get(
                CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN)))
            .orElseThrow();
        setAsteroidDiscovery(asteroid, DiscoveryState.DISCOVERED);

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
            new StarmapAssetActions.OrbitalAssetSupport(),
            new TestCallbacks());
        CelestialObject hiddenAsteroid = hiddenAsteroid();
        setAsteroidDiscovery(hiddenAsteroid, DiscoveryState.HIDDEN);

        controller.triggerAssetCreation(state, hiddenAsteroid, CelestialAsset.Kind.AUTOMATED_OUTPOST, false);

        assertNull(state.pendingAssetCreation);
    }

    private static void setAsteroidDiscovery(CelestialObject asteroid, DiscoveryState detectionState) {
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            asteroid.id()
                                .minorBodyId()
                                .index(),
                            detectionState,
                            CelestialResourceKnowledgeState.UNKNOWN)),
                    List.of())));
    }

    private static CelestialObject hiddenAsteroid() {
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        return AsteroidFieldResolver.resolveAll(
            CelestialObjectId.FROZEN_BELT,
            belt.properties()
                .asteroidFieldProfile())
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.HIDDEN)
            .findFirst()
            .flatMap(node -> CelestialRegistry.get(CelestialObjectKey.minorBody(node.id())))
            .orElseThrow();
    }

    private static final class TestCallbacks implements StarmapAssetActions.OrbitalAssetActionController.Callbacks {

        @Override
        public boolean isCreativeBuildModeEnabled() {
            return false;
        }

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
            StationTransferTarget target) {}
    }
}
