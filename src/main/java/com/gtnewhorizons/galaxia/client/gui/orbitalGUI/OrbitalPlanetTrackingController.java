package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

final class OrbitalPlanetTrackingController {

    enum ClickAction {
        TRACK_ONLY,
        FOCUS_AND_SELECT,
        SELECT_ONLY
    }

    private OrbitalMapClickMode clickMode = OrbitalMapClickMode.HIERARCHY;
    private boolean following;
    private CelestialObject focusedBody;

    OrbitalMapClickMode clickMode() {
        return clickMode;
    }

    void setClickMode(OrbitalMapClickMode clickMode) {
        this.clickMode = clickMode == null ? OrbitalMapClickMode.HIERARCHY : clickMode;
    }

    boolean isFollowing() {
        return following;
    }

    CelestialObject focusedBody() {
        return focusedBody;
    }

    ClickAction clickBody(CelestialObject body, boolean canOpenHierarchy) {
        if (body == null) return ClickAction.SELECT_ONLY;
        if (clickMode == OrbitalMapClickMode.FOLLOW) {
            track(body);
            return ClickAction.TRACK_ONLY;
        }
        if (canOpenHierarchy) return ClickAction.SELECT_ONLY;
        track(body);
        return ClickAction.FOCUS_AND_SELECT;
    }

    void onScrolled() {}

    void onManualCameraMoved() {
        following = false;
    }

    private void track(CelestialObject body) {
        focusedBody = body;
        following = true;
    }
}
