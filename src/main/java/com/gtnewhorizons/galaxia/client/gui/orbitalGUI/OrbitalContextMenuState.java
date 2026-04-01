package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

final class OrbitalContextMenuState {

    private OrbitalCelestialBody body;
    private int x;
    private int y;

    boolean isOpen() {
        return body != null;
    }

    OrbitalCelestialBody body() {
        return body;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    void open(OrbitalCelestialBody body, int x, int y) {
        this.body = body;
        this.x = x;
        this.y = y;
    }

    void close() {
        body = null;
    }
}
