package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleLayerRendererTest {

    @Test
    void footprintTextureBoundsCoverConnectorGapsBetweenModuleTiles() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(0, 0),
            ModuleShape.L_2x2,
            ModuleTier.IV);

        ModuleLayerRenderer.FootprintTextureBounds bounds = ModuleLayerRenderer
            .footprintTextureBounds(module, 200, 200, 0, 0, 0, 0, 0);

        assertEquals(StationMapViewport.tileLeftX(0, 200, 0, 0, 0), bounds.x());
        assertEquals(StationMapViewport.tileTopY(0, 200, 0, 0), bounds.y());
        assertEquals(StationMapViewport.TILE_SIZE * 2 + StationMapViewport.CONNECTOR_GAP, bounds.width());
        assertEquals(StationMapViewport.TILE_SIZE * 2 + StationMapViewport.CONNECTOR_GAP, bounds.height());
    }

    @Test
    void footprintTextureBoundsFollowRotatedModuleTiles() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(0, 0),
            ModuleShape.L_2x2,
            ModuleTier.IV);
        module.setRotation(1);

        ModuleLayerRenderer.FootprintTextureBounds bounds = ModuleLayerRenderer
            .footprintTextureBounds(module, 200, 200, 0, 0, 0, 0, 0);

        assertEquals(StationMapViewport.tileLeftX(-1, 200, 0, 0, 0), bounds.x());
        assertEquals(StationMapViewport.tileTopY(0, 200, 0, 0), bounds.y());
        assertEquals(StationMapViewport.TILE_SIZE * 2 + StationMapViewport.CONNECTOR_GAP, bounds.width());
        assertEquals(StationMapViewport.TILE_SIZE * 2 + StationMapViewport.CONNECTOR_GAP, bounds.height());
    }

    @Test
    void footprintTextureBoundsCanBeComputedForPickerPreview() {
        ModuleLayerRenderer.FootprintTextureBounds bounds = ModuleLayerRenderer
            .footprintTextureBounds(ModuleShape.L_2x2, StationTileCoord.of(0, 0), 1, 200, 200, 0, 0, 0, 0, 0);

        assertEquals(StationMapViewport.tileLeftX(-1, 200, 0, 0, 0), bounds.x());
        assertEquals(StationMapViewport.tileTopY(0, 200, 0, 0), bounds.y());
        assertEquals(StationMapViewport.TILE_SIZE * 2 + StationMapViewport.CONNECTOR_GAP, bounds.width());
        assertEquals(StationMapViewport.TILE_SIZE * 2 + StationMapViewport.CONNECTOR_GAP, bounds.height());
    }

    @Test
    void footprintOverlaySegmentsCoverConnectorGapsWithoutMissingLQuadrant() {
        List<ModuleLayerRenderer.FootprintSegment> segments = ModuleLayerRenderer
            .footprintOverlaySegments(ModuleShape.L_2x2, StationTileCoord.of(0, 0), 0, 200, 200, 0, 0, 0, 0, 0);

        int topLeftX = StationMapViewport.tileLeftX(0, 200, 0, 0, 0);
        int topLeftY = StationMapViewport.tileTopY(0, 200, 0, 0);
        int lowerLeftY = StationMapViewport.tileTopY(1, 200, 0, 0);
        int topRightX = StationMapViewport.tileLeftX(1, 200, 0, 0, 0);

        assertTrue(containsPoint(segments, topLeftX + 1, topLeftY + StationMapViewport.TILE_SIZE));
        assertTrue(containsPoint(segments, topLeftX + StationMapViewport.TILE_SIZE, lowerLeftY + 1));
        assertFalse(containsPoint(segments, topRightX + 1, topLeftY + 1));
    }

    @Test
    void footprintOverlaySegmentsCoverCenterGapWhenFourTilesMeet() {
        List<ModuleLayerRenderer.FootprintSegment> segments = ModuleLayerRenderer
            .footprintOverlaySegments(ModuleShape.QUAD_2x2, StationTileCoord.of(0, 0), 0, 200, 200, 0, 0, 0, 0, 0);

        int centerGapX = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE;
        int centerGapY = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE;

        assertTrue(containsPoint(segments, centerGapX, centerGapY));
    }

    @Test
    void textureRegionFollowsRotatedModuleFootprint() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(4, 4),
            ModuleShape.QUAD_2x2,
            ModuleTier.IV);
        module.setRotation(1);

        ModuleLayerRenderer.TextureRegion anchor = ModuleLayerRenderer.textureRegion(module, StationTileCoord.of(4, 4));
        ModuleLayerRenderer.TextureRegion left = ModuleLayerRenderer.textureRegion(module, StationTileCoord.of(3, 4));
        ModuleLayerRenderer.TextureRegion lowerLeft = ModuleLayerRenderer
            .textureRegion(module, StationTileCoord.of(3, 5));

        assertRegion(anchor, 0f, 0f, 0.5f, 0.5f);
        assertRegion(left, 0f, 0.5f, 0.5f, 1f);
        assertRegion(lowerLeft, 0.5f, 0.5f, 1f, 1f);
    }

    private static void assertRegion(ModuleLayerRenderer.TextureRegion region, float u0, float v0, float u1, float v1) {
        assertEquals(u0, region.u0());
        assertEquals(v0, region.v0());
        assertEquals(u1, region.u1());
        assertEquals(v1, region.v1());
    }

    private static boolean containsPoint(List<ModuleLayerRenderer.FootprintSegment> segments, int x, int y) {
        return segments.stream()
            .anyMatch(
                segment -> x >= segment.x() && x < segment.x() + segment.width()
                    && y >= segment.y()
                    && y < segment.y() + segment.height());
    }
}
