package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StarmapAssetActionsTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void asteroidAssetActionsAllowOutpostButRejectAutomatedStationCreation() {
        StarmapAssetActions.OrbitalAssetUiState state = new StarmapAssetActions.OrbitalAssetUiState();
        StarmapAssetActions.OrbitalAssetActionController controller = new StarmapAssetActions.OrbitalAssetActionController(
            new StarmapAssetActions.OrbitalAssetSupport(),
            new TestCallbacks());
        CelestialObject asteroid = CelestialRegistry
            .get(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)))
            .orElseThrow();

        controller.triggerAssetCreation(state, asteroid, CelestialAsset.Kind.AUTOMATED_STATION, false);

        assertNull(state.pendingAssetCreation);

        controller.triggerAssetCreation(state, asteroid, CelestialAsset.Kind.AUTOMATED_OUTPOST, false);

        assertNotNull(state.pendingAssetCreation);
        assertEquals(CelestialAsset.Kind.AUTOMATED_OUTPOST, state.pendingAssetCreation.kind());
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
