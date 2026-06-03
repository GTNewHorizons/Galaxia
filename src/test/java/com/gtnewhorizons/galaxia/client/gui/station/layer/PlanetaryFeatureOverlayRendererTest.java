package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

final class PlanetaryFeatureOverlayRendererTest {

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

    @Test
    void sortsFeatureDefinitionsByLayerBeforeDrawing() {
        List<PlanetaryFeatureDefinition> definitions = PlanetaryFeatureOverlayRenderer.sortedDefinitions(
            List.of(
                PlanetaryFeatureRegistry.MINERAL_VEIN.key(),
                PlanetaryFeatureRegistry.THERMAL_SINK_ZONE.key(),
                PlanetaryFeatureRegistry.REGOLITH_FLATS.key()));

        assertEquals(
            List.of(
                PlanetaryFeatureRegistry.REGOLITH_FLATS.key(),
                PlanetaryFeatureRegistry.THERMAL_SINK_ZONE.key(),
                PlanetaryFeatureRegistry.MINERAL_VEIN.key()),
            definitions.stream()
                .map(PlanetaryFeatureDefinition::key)
                .toList());
    }
}
