package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.Collection;
import java.util.function.Function;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

/**
 * The starmap's zoom scale.
 * <p>
 * Zoom is stored as a level, not a factor: one step multiplies the on-screen scale by {@link #ZOOM_BASE}, so the whole
 * range from a moon's surface to the galaxy fits in a few hundred levels and every zoom animation can lerp linearly.
 * <p>
 * Everything here is a pure function of distances and viewport size, so the clamps and fallbacks that decide whether
 * the map lands on the target or on empty space can be exercised directly.
 */
final class OrbitalZoom {

    /** One zoom level multiplies the rendered scale by this factor. */
    static final double ZOOM_BASE = 1.18;
    /** Pixels per world unit at zoom level zero. */
    static final double BASE_SCALE = 82.0;

    static final double OVERVIEW_SCREEN_RADIUS = 420.0;
    static final double ISO_OVERVIEW_SCREEN_RADIUS = 350.0;

    private static final double MIN_ZOOM = -7000.0;
    private static final double MAX_ZOOM = 14000.0;
    /** Where the camera lands when there is nothing to frame. */
    private static final double DEFAULT_ZOOM = -0.8;
    private static final double DEFAULT_ISO_ZOOM = 3.0;
    private static final double NEGLIGIBLE = 1e-9;

    private static final double FALLBACK_VIEWPORT_WIDTH = 960.0;
    private static final double FALLBACK_VIEWPORT_HEIGHT = 640.0;

    private OrbitalZoom() {}

    static double scaleForZoomLevel(double zoomLevel) {
        return BASE_SCALE * Math.pow(ZOOM_BASE, zoomLevel);
    }

    static double clampZoom(double zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    /** The zoom level at which {@code worldDistance} spans {@code screenDistance} pixels. */
    static double zoomForWorldDistance(double worldDistance, double screenDistance) {
        if (worldDistance <= NEGLIGIBLE || screenDistance <= NEGLIGIBLE) return DEFAULT_ZOOM;
        return clampZoom(Math.log((screenDistance / worldDistance) / BASE_SCALE) / Math.log(ZOOM_BASE));
    }

    /** Frames {@code extent}, falling back to a default when the body has nothing around it to frame. */
    static double overviewZoomForExtent(double extent, boolean isometric) {
        if (extent <= NEGLIGIBLE) return isometric ? DEFAULT_ISO_ZOOM : DEFAULT_ZOOM;
        return zoomForWorldDistance(extent, isometric ? ISO_OVERVIEW_SCREEN_RADIUS : OVERVIEW_SCREEN_RADIUS);
    }

    /** Galaxies and stars are shown flat; anything smaller gets the isometric overview. */
    static boolean useIsometricOverview(CelestialObject body) {
        return body.objectClass() != CelestialObject.Class.GALAXY && body.objectClass() != CelestialObject.Class.STAR;
    }

    static double viewportHalfDiagonal(int width, int height) {
        return Math.hypot(viewportWidth(width) * 0.5, viewportHeight(height) * 0.5);
    }

    static double viewportMinDimension(int width, int height) {
        return Math.min(viewportWidth(width), viewportHeight(height));
    }

    /** Distance to the closest other star, or {@link Double#MAX_VALUE} when there is no second star to measure to. */
    static double nearestOtherStarDistance(CelestialObject anchorStar, Collection<CelestialObject> galaxyBodies,
        Function<CelestialObject, double[]> worldPositionProvider) {
        if (anchorStar == null || galaxyBodies == null || worldPositionProvider == null) return Double.MAX_VALUE;
        double[] anchorPos = worldPositionProvider.apply(anchorStar);
        if (anchorPos == null) return Double.MAX_VALUE;
        double nearestDistance = Double.MAX_VALUE;
        for (CelestialObject body : galaxyBodies) {
            if (body == anchorStar || body.objectClass() != CelestialObject.Class.STAR) continue;
            double[] bodyPos = worldPositionProvider.apply(body);
            if (bodyPos == null) continue;
            nearestDistance = Math
                .min(nearestDistance, Math.hypot(bodyPos[0] - anchorPos[0], bodyPos[1] - anchorPos[1]));
        }
        return nearestDistance;
    }

    /**
     * A widget that has not been laid out yet reports a zero area, which would otherwise divide the zoom by nothing.
     */
    private static double viewportWidth(int width) {
        return width > 0 ? width : FALLBACK_VIEWPORT_WIDTH;
    }

    private static double viewportHeight(int height) {
        return height > 0 ? height : FALLBACK_VIEWPORT_HEIGHT;
    }
}
