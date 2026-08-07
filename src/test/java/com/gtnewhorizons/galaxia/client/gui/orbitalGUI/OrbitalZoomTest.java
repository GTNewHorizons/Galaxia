package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalZoomTest {

    @Test
    void framingAWorldDistanceMakesItSpanTheRequestedPixels() {
        double worldDistance = 4_500.0;
        double screenDistance = 420.0;

        double zoom = OrbitalZoom.zoomForWorldDistance(worldDistance, screenDistance);

        assertEquals(screenDistance, OrbitalZoom.scaleForZoomLevel(zoom) * worldDistance, 1.0e-6);
    }

    @Test
    void framingNothingFallsBackToTheDefaultZoom() {
        assertEquals(-0.8, OrbitalZoom.zoomForWorldDistance(0.0, 420.0), 1.0e-9);
        assertEquals(-0.8, OrbitalZoom.zoomForWorldDistance(4_500.0, 0.0), 1.0e-9);
    }

    @Test
    void zoomStaysInsideItsClampedRange() {
        assertEquals(-7000.0, OrbitalZoom.clampZoom(-1.0e9), 1.0e-9);
        assertEquals(14000.0, OrbitalZoom.clampZoom(1.0e9), 1.0e-9);
        assertEquals(12.5, OrbitalZoom.clampZoom(12.5), 1.0e-9);
    }

    @Test
    void anExtremeWorldDistanceStillProducesAUsableZoomLevel() {
        // The clamp is defensive only: even the widest finite distance lands near -4300, well inside the range.
        double zoom = OrbitalZoom.zoomForWorldDistance(Double.MAX_VALUE, 420.0);

        assertTrue(Double.isFinite(zoom));
        assertEquals(zoom, OrbitalZoom.clampZoom(zoom), 1.0e-9);
    }

    @Test
    void anEmptyBodyGetsADefaultOverviewPerProjection() {
        assertEquals(-0.8, OrbitalZoom.overviewZoomForExtent(0.0, false), 1.0e-9);
        assertEquals(3.0, OrbitalZoom.overviewZoomForExtent(0.0, true), 1.0e-9);
    }

    @Test
    void isometricOverviewAppliesBelowStarScale() {
        assertFalse(OrbitalZoom.useIsometricOverview(body(CelestialObjectId.ILIA, CelestialObject.Class.STAR)));
        assertFalse(OrbitalZoom.useIsometricOverview(body(CelestialObjectId.VAEL, CelestialObject.Class.GALAXY)));
        assertTrue(OrbitalZoom.useIsometricOverview(body(CelestialObjectId.ROMULUS, CelestialObject.Class.PLANET)));
    }

    @Test
    void anUnlaidOutViewportUsesItsFallbackDimensions() {
        assertEquals(Math.hypot(480.0, 320.0), OrbitalZoom.viewportHalfDiagonal(0, 0), 1.0e-9);
        assertEquals(640.0, OrbitalZoom.viewportMinDimension(0, 0), 1.0e-9);
        assertEquals(300.0, OrbitalZoom.viewportMinDimension(300, 800), 1.0e-9);
    }

    @Test
    void nearestOtherStarDistanceUsesGalaxyStarsAndIgnoresPlanets() {
        CelestialObject anchorStar = body(CelestialObjectId.ILIA, CelestialObject.Class.STAR);
        CelestialObject otherStar = body(CelestialObjectId.VAEL, CelestialObject.Class.STAR);
        CelestialObject nearbyPlanet = body(CelestialObjectId.ROMULUS, CelestialObject.Class.PLANET);
        Map<CelestialObject, double[]> positions = new HashMap<>();
        positions.put(anchorStar, new double[] { 0.0, 0.0 });
        positions.put(otherStar, new double[] { 1000.0, 0.0 });
        positions.put(nearbyPlanet, new double[] { 10.0, 0.0 });

        double distance = OrbitalZoom
            .nearestOtherStarDistance(anchorStar, List.of(anchorStar, otherStar, nearbyPlanet), positions::get);

        assertEquals(1000.0, distance);
    }

    @Test
    void aLoneStarReportsNoNeighbour() {
        CelestialObject anchorStar = body(CelestialObjectId.ILIA, CelestialObject.Class.STAR);
        Map<CelestialObject, double[]> positions = new HashMap<>();
        positions.put(anchorStar, new double[] { 0.0, 0.0 });

        assertEquals(
            Double.MAX_VALUE,
            OrbitalZoom.nearestOtherStarDistance(anchorStar, List.of(anchorStar), positions::get));
    }

    @Test
    void starsWithoutAResolvedPositionAreSkipped() {
        CelestialObject anchorStar = body(CelestialObjectId.ILIA, CelestialObject.Class.STAR);
        CelestialObject placedStar = body(CelestialObjectId.VAEL, CelestialObject.Class.STAR);
        CelestialObject unplacedStar = body(CelestialObjectId.EGORA, CelestialObject.Class.STAR);
        Map<CelestialObject, double[]> positions = new HashMap<>();
        positions.put(anchorStar, new double[] { 0.0, 0.0 });
        positions.put(placedStar, new double[] { 700.0, 0.0 });

        double distance = OrbitalZoom
            .nearestOtherStarDistance(anchorStar, List.of(anchorStar, unplacedStar, placedStar), positions::get);

        assertEquals(700.0, distance);
    }

    private static CelestialObject body(CelestialObjectId id, CelestialObject.Class objectClass) {
        return CelestialObject.builder()
            .key(id)
            .name(id.name())
            .objectClass(objectClass)
            .build();
    }
}
