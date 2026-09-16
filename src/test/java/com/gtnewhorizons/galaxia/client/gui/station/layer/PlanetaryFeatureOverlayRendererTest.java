package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.gui.station.StationMapFrame;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class PlanetaryFeatureOverlayRendererTest {

    @Test
    void visibleProjectionMatchesAuthoritativeFeaturesAfterPanAndSaltChanges() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        var projection = new PlanetaryFeatureOverlayRenderer.VisibleFeatures();
        List<StationMapFrame.TilePosition> tiles = new ArrayList<>();
        for (int pan : new int[] { 0, 17, 56, -84 }) {
            new StationMapFrame(280, 200, 20, 8, 10, pan, -pan).collectVisibleTilePositions(tiles);
            for (long salt : new long[] { 123L, 123L, 987L }) {
                facility.setStationFeatureSalt(salt);
                assertEquals(
                    tiles.stream()
                        .map(tile -> facility.planetaryFeaturesAt(tile.dx(), tile.dy()))
                        .toList(),
                    projection.project(facility, tiles));
            }
        }
    }

    @Test
    void centersLargerFeatureTextureOverTile() {
        PlanetaryFeatureOverlayRenderer.TileOverlay overlay = PlanetaryFeatureOverlayRenderer.centeredOverlay(
            100,
            50,
            new ResourceLocation("galaxia", "textures/gui/station/features/regolith_flats.png"),
            32,
            32);

        assertEquals(96, overlay.x());
        assertEquals(46, overlay.y());
        assertEquals(32, overlay.width());
        assertEquals(32, overlay.height());
    }

    @Test
    void centersSmallerFeatureTextureOverTile() {
        PlanetaryFeatureOverlayRenderer.TileOverlay overlay = PlanetaryFeatureOverlayRenderer.centeredOverlay(
            100,
            50,
            new ResourceLocation("galaxia", "textures/gui/station/features/mineral_vein.png"),
            8,
            8);

        assertEquals(108, overlay.x());
        assertEquals(58, overlay.y());
    }

    @Test
    void featureLayersDrawTerrainBelowEnvironmentBelowResources() {
        assertTrue(PlanetaryFeatureLayer.TERRAIN.drawOrder() < PlanetaryFeatureLayer.ENVIRONMENT.drawOrder());
        assertTrue(PlanetaryFeatureLayer.ENVIRONMENT.drawOrder() < PlanetaryFeatureLayer.RESOURCE.drawOrder());
    }
}
