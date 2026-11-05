package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

/**
 * The starmap's sense of orbital time.
 * <p>
 * The server owns the authoritative orbital clock. The map shows its own display clock, anchored to the server one so
 * that pausing or running at a different time scale never makes the two drift apart permanently: on every re-anchor the
 * display time is pinned to the server time it corresponded to, and from there the two advance in proportion.
 * <p>
 * The caller supplies the server reading and whether a world is loaded, so this class holds no Minecraft state and its
 * anchoring can be exercised directly.
 */
final class OrbitalClock {

    private final double serverUnitsPerSecond;

    private double time;
    private double timeScale;
    private boolean paused;
    private long lastFrameMs = System.currentTimeMillis();
    private double displayAnchor;
    private double serverAnchor = Double.NaN;
    private int revision = Integer.MIN_VALUE;

    OrbitalClock(double timeScale, double serverUnitsPerSecond) {
        this.timeScale = timeScale;
        this.serverUnitsPerSecond = serverUnitsPerSecond;
    }

    double time() {
        return time;
    }

    double timeScale() {
        return timeScale;
    }

    boolean isPaused() {
        return paused;
    }

    /** Bumps whenever the display clock is re-pinned, so caches keyed on orbital time know to rebuild. */
    int revision() {
        return revision;
    }

    /**
     * Advances one frame. Without a world there is no server clock to follow, so the display clock free-runs at its
     * own time scale; with one it tracks the server reading.
     */
    void advance(boolean inWorld, double serverOrbitalTime) {
        long now = System.currentTimeMillis();
        double elapsedSeconds = (now - lastFrameMs) / 1000.0;
        lastFrameMs = now;
        if (!inWorld) {
            if (!paused) time += elapsedSeconds * timeScale;
            return;
        }
        if (Double.isNaN(serverAnchor)) {
            anchor(serverOrbitalTime, serverOrbitalTime);
            time = serverOrbitalTime;
            return;
        }
        time = paused ? displayAnchor : toDisplayTime(serverOrbitalTime);
    }

    /** The display time right now, without advancing the clock. */
    double captureDisplayTime(boolean inWorld, double serverOrbitalTime) {
        if (!inWorld) return time;
        if (Double.isNaN(serverAnchor)) return serverOrbitalTime;
        return paused ? displayAnchor : toDisplayTime(serverOrbitalTime);
    }

    /** Converts a server orbital time into the display time it maps to under the current anchor. */
    double toDisplayTime(double serverOrbitalTime) {
        if (Double.isNaN(serverAnchor)) return serverOrbitalTime;
        return displayAnchor + (serverOrbitalTime - serverAnchor) * (timeScale / serverUnitsPerSecond);
    }

    /** Pins the display clock to {@code displayTime} and re-reads the server clock underneath it. */
    void reanchor(double displayTime, boolean inWorld, double serverOrbitalTime) {
        time = displayTime;
        if (!inWorld) return;
        anchor(displayTime, serverOrbitalTime);
    }

    /**
     * Pauses or resumes without jumping. The display time is captured before the flip, then re-pinned after it, so the
     * map resumes from exactly where it stopped.
     */
    void togglePaused(boolean inWorld, double serverOrbitalTime) {
        double displayTime = captureDisplayTime(inWorld, serverOrbitalTime);
        paused = !paused;
        reanchor(displayTime, inWorld, serverOrbitalTime);
    }

    private void anchor(double displayTime, double serverOrbitalTime) {
        displayAnchor = displayTime;
        serverAnchor = serverOrbitalTime;
        revision++;
    }
}
