package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

/**
 * A still starmap view for tests: a fixed viewport, an identity world-to-screen projection and a stopped clock.
 * <p>
 * Fields are mutable so a test can move whichever part of the view it is actually about and leave the rest alone.
 */
final class TestStarmapView implements StarmapViewContext {

    int viewportWidth = 960;
    int viewportHeight = 640;
    double scale = 1.0;
    double currentTime = 0.0;
    double timeScale = 1.0;
    double serverOrbitalTime = 0.0;
    boolean creativeBuildMode = false;
    boolean gt5AutomationAvailable = false;

    @Override
    public int viewportWidth() {
        return viewportWidth;
    }

    @Override
    public int viewportHeight() {
        return viewportHeight;
    }

    @Override
    public float worldToScreenX(double worldX) {
        return (float) worldX;
    }

    @Override
    public float worldToScreenY(double worldY) {
        return (float) worldY;
    }

    @Override
    public double scale() {
        return scale;
    }

    @Override
    public double currentTime() {
        return currentTime;
    }

    @Override
    public double timeScale() {
        return timeScale;
    }

    @Override
    public double serverOrbitalTime() {
        return serverOrbitalTime;
    }

    @Override
    public boolean creativeBuildMode() {
        return creativeBuildMode;
    }

    @Override
    public boolean gt5AutomationAvailable() {
        return gt5AutomationAvailable;
    }
}
