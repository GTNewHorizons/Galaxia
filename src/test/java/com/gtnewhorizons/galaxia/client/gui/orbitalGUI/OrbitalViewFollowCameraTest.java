package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        for (int i = 0; i < 9; i++) fast.step(0.3, 1.0 / 60);
        for (int i = 0; i < 3; i++) slow.step(0.3, 1.0 / 20);
        assertTrue(fast.zoomLevel > 0 && fast.zoomLevel < fast.targetZoomLevel);
        assertEquals(fast.cameraX, slow.cameraX, 1e-8);
        assertEquals(fast.cameraY, slow.cameraY, 1e-8);
        assertEquals(fast.zoomLevel, slow.zoomLevel, 1e-8);
        assertEquals(fast.isometricProgress, slow.isometricProgress, 1e-8);
    }

    /** Product contract: an animation reaches its destination in the requested time, regardless of distance. */
    @Test
    void animationCompletesWithoutADistanceDependentTail() {
        for (double distance : new double[] { 1, 1_000, 1_000_000 }) {
            var state = new OrbitalView.OrbitalViewState(0);
            state.targetCameraX = distance;
            state.targetCameraY = -distance;
            state.targetZoomLevel = 40;
            state.targetIsometricProgress = 1;
            state.step(0.3, 0.3);
            assertEquals(distance, state.cameraX);
            assertEquals(-distance, state.cameraY);
            assertEquals(40, state.zoomLevel);
            assertEquals(1, state.isometricProgress);
        }
    }

    /** Product contract: retargeting starts at the currently displayed position, without jumping. */
    @Test
    void changingAnimationTargetKeepsTheCurrentView() {
        var state = new OrbitalView.OrbitalViewState(0);
        state.targetZoomLevel = 40;
        state.step(0.3, 0.15);
        double before = state.zoomLevel;
        state.targetZoomLevel = -20;
        state.step(0.3, 0);
        assertEquals(before, state.zoomLevel);
        state.step(0.3, 0.3);
        assertEquals(-20, state.zoomLevel);
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
