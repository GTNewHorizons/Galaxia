package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class OrbitalViewFollowCameraTest {

    @Test
    void followingCameraSnapKeepsTrackedBodyCenteredWithoutSnappingZoom() {
        OrbitalView.OrbitalViewState state = new OrbitalView.OrbitalViewState(-0.8);
        state.cameraX = 10.0;
        state.cameraY = 20.0;
        state.targetCameraX = 1000.0;
        state.targetCameraY = -500.0;
        state.zoomLevel = 4.0;
        state.targetZoomLevel = 8.0;

        state.syncCameraToTarget();

        assertEquals(1000.0, state.cameraX);
        assertEquals(-500.0, state.cameraY);
        assertEquals(4.0, state.zoomLevel);
    }
}
