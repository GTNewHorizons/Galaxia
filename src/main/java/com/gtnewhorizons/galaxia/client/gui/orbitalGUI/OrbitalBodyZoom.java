package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

/**
 * Picks the zoom level that frames a given celestial body.
 * <p>
 * What counts as "framed" depends on where you are looking from. A galaxy is framed by the distance to its outermost
 * star, a system by its widest orbit, and a focused body by the orbits of its siblings — so the same request means a
 * different measurement at each layer. Switching between layers needs two more: how far out to pull before cutting
 * away from a system, and how far in the galaxy sits on the other side of that cut.
 * <p>
 * Everything the celestial tree has to answer arrives through {@link World}, so the layer rules can be exercised
 * against a handful of fake bodies instead of a loaded save.
 */
final class OrbitalBodyZoom {

    /** How far past its outermost orbit a system is pulled before the map cuts to the galaxy. */
    private static final double SYSTEM_DEPARTURE_EXTENT_MULTIPLIER = 24.0;
    private static final double NEGLIGIBLE = 1e-9;

    /** What the zoom needs to know about the celestial tree. Read-only, and small enough to fake in a test. */
    interface World {

        List<CelestialObject> childrenOf(CelestialObject body);

        /** Position in world space, or {@code null} when the body has not been placed this frame. */
        double[] absolutePosition(CelestialObject body);

        CelestialObject parentOf(CelestialObject body);
    }

    private final CelestialObject root;
    private final World world;
    private final StarmapViewContext view;

    OrbitalBodyZoom(CelestialObject root, World world, StarmapViewContext view) {
        this.root = root;
        this.world = world;
        this.view = view;
    }

    /** A galaxy is measured by how far its stars sit from the centre; anything else by its widest child orbit. */
    double overviewExtent(CelestialObject body) {
        if (body.objectClass() == CelestialObject.Class.GALAXY) {
            double maxDistance = 0.0;
            for (CelestialObject child : world.childrenOf(body)) {
                double[] pos = world.absolutePosition(child);
                if (pos == null) continue;
                maxDistance = Math.max(maxDistance, Math.hypot(pos[0], pos[1]));
            }
            return maxDistance;
        }
        double maxSize = 0.0;
        for (CelestialObject child : world.childrenOf(body)) maxSize = Math.max(
            maxSize,
            child.orbitalParams()
                .apogee());
        return maxSize;
    }

    /** A focused body is framed against its siblings, so neighbouring orbits stay on screen. */
    double focusedOrbitExtent(CelestialObject body) {
        CelestialObject parent = world.parentOf(body);
        if (parent == null) return 0.0;
        double maxApogee = 0.0;
        for (CelestialObject sibling : world.childrenOf(parent)) maxApogee = Math.max(
            maxApogee,
            sibling.orbitalParams()
                .apogee());
        return maxApogee;
    }

    double overviewZoomFor(CelestialObject body) {
        return overviewZoom(body, OrbitalZoom.useIsometricOverview(body));
    }

    double overviewZoom(CelestialObject body, boolean isometric) {
        double extent = isometric ? focusedOrbitExtent(body) : overviewExtent(body);
        return OrbitalZoom.overviewZoomForExtent(extent, isometric);
    }

    /** How far out the system is pulled just before the map cuts away from it. */
    double systemDepartureZoom(CelestialObject star) {
        return OrbitalZoom.zoomForWorldDistance(
            overviewExtent(star) * SYSTEM_DEPARTURE_EXTENT_MULTIPLIER,
            OrbitalZoom.OVERVIEW_SCREEN_RADIUS);
    }

    double nearestOtherStarDistance(CelestialObject anchorStar) {
        return OrbitalZoom.nearestOtherStarDistance(anchorStar, world.childrenOf(root), world::absolutePosition);
    }

    /** Where the galaxy settles after the cut. A lone star has no neighbour to scale against, so the root frames it. */
    double galaxyOverviewZoom(CelestialObject anchorStar) {
        double nearestDistance = nearestOtherStarDistance(anchorStar);
        if (nearestDistance == Double.MAX_VALUE || nearestDistance <= NEGLIGIBLE) return overviewZoomFor(root);
        return OrbitalZoom.zoomForWorldDistance(
            nearestDistance,
            OrbitalZoom.viewportMinDimension(viewportWidth(), viewportHeight()) * 0.2);
    }

    /** The galaxy-side zoom the cut lands on: closer in than the overview, which the map then eases out to. */
    double galaxyCutZoom(CelestialObject anchorStar) {
        double nearestDistance = nearestOtherStarDistance(anchorStar);
        if (nearestDistance == Double.MAX_VALUE || nearestDistance <= NEGLIGIBLE) return galaxyOverviewZoom(anchorStar);
        return OrbitalZoom.zoomForWorldDistance(
            nearestDistance,
            OrbitalZoom.viewportHalfDiagonal(viewportWidth(), viewportHeight()) * 1.5);
    }

    private int viewportWidth() {
        return view.viewportWidth();
    }

    private int viewportHeight() {
        return view.viewportHeight();
    }
}
