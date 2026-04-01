package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

final class OrbitalLayerTransitionState {

    OrbitalCelestialBody pendingTarget;
    OrbitalCelestialBody pendingAnchor;
    double pendingStartZoom;
    double pendingTargetZoom;
    Phase phase = Phase.NONE;
    OrbitalCelestialBody activeTarget;
    OrbitalCelestialBody activeAnchor;
    double activeStartZoom;
    double activeTargetZoom;
    float activeStartSpriteSize;
    float activeTargetSpriteSize;

    boolean hasPending() {
        return pendingTarget != null && pendingAnchor != null;
    }

    boolean isActive() {
        return phase != Phase.NONE;
    }

    void beginPending(OrbitalCelestialBody target, OrbitalCelestialBody anchor, double startZoom, double targetZoom) {
        pendingTarget = target;
        pendingAnchor = anchor;
        pendingStartZoom = startZoom;
        pendingTargetZoom = targetZoom;
    }

    void clearPending() {
        pendingTarget = null;
        pendingAnchor = null;
        pendingStartZoom = 0.0;
        pendingTargetZoom = 0.0;
    }

    void beginActive(Phase nextPhase, OrbitalCelestialBody target, OrbitalCelestialBody anchor, double startZoom,
        double targetZoom, float startSpriteSize, float targetSpriteSize) {
        phase = nextPhase;
        activeTarget = target;
        activeAnchor = anchor;
        activeStartZoom = startZoom;
        activeTargetZoom = targetZoom;
        activeStartSpriteSize = startSpriteSize;
        activeTargetSpriteSize = targetSpriteSize;
    }

    void clearActive() {
        phase = Phase.NONE;
        activeTarget = null;
        activeAnchor = null;
        activeStartZoom = 0.0;
        activeTargetZoom = 0.0;
        activeStartSpriteSize = 0.0f;
        activeTargetSpriteSize = 0.0f;
    }

    void clear() {
        clearPending();
        clearActive();
    }

    enum Phase {
        NONE,
        SYSTEM_PRE_CUT,
        SYSTEM_POST_CUT,
        GALAXY_PRE_CUT,
        GALAXY_POST_CUT
    }
}
