package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

final class PlanetaryFeatureOverlayRendererTest {

    @Test
    void laysOutFeatureMarkersInsideBottomLeftCornerOfTile() {
        List<PlanetaryFeatureOverlayRenderer.Marker> markers = PlanetaryFeatureOverlayRenderer.markers(
            100,
            50,
            List.of(
                PlanetaryFeatureRegistry.STABLE_BEDROCK,
                PlanetaryFeatureRegistry.MINERAL_VEIN,
                PlanetaryFeatureRegistry.GEOTHERMAL_VENT));

        assertEquals(3, markers.size());
        assertEquals(
            102,
            markers.get(0)
                .x());
        assertEquals(
            68,
            markers.get(0)
                .y());
        assertEquals(
            107,
            markers.get(1)
                .x());
        assertEquals(
            68,
            markers.get(1)
                .y());
        assertEquals(
            112,
            markers.get(2)
                .x());
        assertEquals(
            68,
            markers.get(2)
                .y());
    }

    @Test
    void keepsOnlyRenderableMarkersThatFitInTile() {
        List<PlanetaryFeatureOverlayRenderer.Marker> markers = PlanetaryFeatureOverlayRenderer.markers(
            0,
            0,
            List.of(
                PlanetaryFeatureRegistry.REGOLITH_FLATS,
                PlanetaryFeatureRegistry.STABLE_BEDROCK,
                PlanetaryFeatureRegistry.MINERAL_VEIN,
                PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET,
                PlanetaryFeatureRegistry.GEOTHERMAL_VENT));

        assertEquals(4, markers.size());
    }
}
