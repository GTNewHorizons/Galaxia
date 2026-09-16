package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class OrbitalViewFollowCameraTest {

    /** Bug regression: slower rendering must not slow camera, zoom, or view transitions. */
    @Test
    void animationProgressDependsOnElapsedTimeRatherThanFrameCount() {
        var fast = new OrbitalView.OrbitalViewState(0);
        var slow = new OrbitalView.OrbitalViewState(0);
        for (var state : new OrbitalView.OrbitalViewState[] { fast, slow }) {
            state.targetCameraX = 1000;
            state.targetCameraY = -400;
            state.targetZoomLevel = 20;
            state.targetIsometricProgress = 1;
        }
        for (int i = 0; i < 60; i++) fast.step(0.045, 1.0 / 60);
        for (int i = 0; i < 15; i++) slow.step(0.045, 1.0 / 15);
        assertEquals(fast.cameraX, slow.cameraX, 1e-8);
        assertEquals(fast.cameraY, slow.cameraY, 1e-8);
        assertEquals(fast.zoomLevel, slow.zoomLevel, 1e-8);
        assertEquals(fast.isometricProgress, slow.isometricProgress, 1e-8);
    }

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
