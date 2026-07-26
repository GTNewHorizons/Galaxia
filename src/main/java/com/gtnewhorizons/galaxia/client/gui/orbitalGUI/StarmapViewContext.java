package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

/**
 * What the starmap's collaborators are allowed to know about the current view.
 * <p>
 * Every renderer, panel and modal on the starmap needs some of the same things: how big the viewport is, where a world
 * coordinate lands on screen, what time it is, and whether the player is in creative build mode. Before this interface
 * each of them declared its own copy on its own {@code Callbacks}, and the map widget implemented the same accessor
 * five times over.
 * <p>
 * This is deliberately read-only. Anything that changes the view — opening a modal, moving the camera, sending a
 * packet — stays on the owning collaborator's {@code Callbacks}, so the difference between reading the view and
 * driving it is visible in the type.
 */
interface StarmapViewContext {

    int viewportWidth();

    int viewportHeight();

    float worldToScreenX(double worldX);

    float worldToScreenY(double worldY);

    /** Pixels per world unit at the current zoom. */
    double scale();

    /** The orbital time the map is displaying, which pauses and scales independently of the server clock. */
    double currentTime();

    /** How fast display time advances against server time. */
    double timeScale();

    /** The server's authoritative orbital time. */
    double serverOrbitalTime();

    boolean creativeBuildMode();

    boolean gt5AutomationAvailable();
}
