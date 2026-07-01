package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationMapWidgetTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void alertBadgeStaysOnOccupiedTileForRotatedNonRectangularModules() {
        for (int rotation = 0; rotation < 4; rotation++) {
            ModuleInstance module = FacilityModuleRegistry.create(
                ModuleInstance.ID.create(),
                FacilityModuleKind.MACERATOR,
                StationTileCoord.CORE,
                ModuleShape.L_2x2,
                ModuleTier.EV);
            module.setRotation(rotation);
            Map<StationTileCoord, PlacedTile> tiles = new LinkedHashMap<>();
            for (StationTileCoord tile : module.tiles()) {
                tiles.put(tile, new PlacedTile(module, StationTileState.OCCUPIED_OPERATIONAL));
            }

            assertTrue(tiles.containsKey(StationMapWidget.alertBadgeCoord(module, tiles)));
        }
    }

    @Test
    void moduleFootprintHitTestSelectsModuleThroughConnectorGapCoveredByTexture() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.CORE,
            ModuleShape.L_2x2,
            ModuleTier.EV);
        layout.place(module);

        int x = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE / 2;
        int y = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE
            + StationMapViewport.CONNECTOR_GAP / 2;

        assertEquals(
            StationTileCoord.CORE,
            StationMapWidget.hitTestModuleFootprint(layout, x, y, 200, 200, 0, 0, 0, 0, 0));
    }

    @Test
    void moduleFootprintHitTestSelectsModuleThroughCenterGapWhereFourTilesMeet() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.CORE,
            ModuleShape.QUAD_2x2,
            ModuleTier.EV);
        layout.place(module);

        int x = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE
            + StationMapViewport.CONNECTOR_GAP / 2;
        int y = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE
            + StationMapViewport.CONNECTOR_GAP / 2;

        assertEquals(
            StationTileCoord.CORE,
            StationMapWidget.hitTestModuleFootprint(layout, x, y, 200, 200, 0, 0, 0, 0, 0));
    }

    @Test
    void moduleFootprintHitTestSelectsAnchorWhenClickingNonAnchorTile() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.CORE,
            ModuleShape.L_2x2,
            ModuleTier.EV);
        layout.place(module);

        int x = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE / 2;
        int y = StationMapViewport.tileTopY(1, 200, 0, 0) + StationMapViewport.TILE_SIZE / 2;

        assertEquals(
            StationTileCoord.CORE,
            StationMapWidget.hitTestModuleFootprint(layout, x, y, 200, 200, 0, 0, 0, 0, 0));
    }

    @Test
    void moduleOverlaySegmentsSkipInternalBordersBetweenConnectedTiles() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.CORE,
            ModuleShape.L_2x2,
            ModuleTier.EV);
        List<ModuleFootprintProjection.Segment> segments = StationMapWidget
            .moduleOverlaySegments(module, 200, 200, 0, 0, 0, 0, 0);
        int xInsideLeftColumn = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE / 2;
        int internalTopBottomJoin = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE;
        int leftOuterEdge = StationMapViewport.tileLeftX(0, 200, 0, 0, 0);
        int yInsideTopTile = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE / 2;

        assertFalse(covers(segments, xInsideLeftColumn, internalTopBottomJoin));
        assertTrue(covers(segments, leftOuterEdge, yInsideTopTile));
    }

    @Test
    void moduleOverlaySegmentsSkipInternalBorderAroundCenterGapWhereFourTilesMeet() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.CORE,
            ModuleShape.QUAD_2x2,
            ModuleTier.EV);
        List<ModuleFootprintProjection.Segment> segments = StationMapWidget
            .moduleOverlaySegments(module, 200, 200, 0, 0, 0, 0, 0);
        int centerGapX = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE;
        int centerGapY = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE;

        assertFalse(covers(segments, centerGapX, centerGapY));
    }

    @Test
    void moduleOverlaySegmentsKeepMissingCornerOutOfLShapedFootprint() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.CORE,
            ModuleShape.L_2x2,
            ModuleTier.EV);
        List<ModuleFootprintProjection.Segment> segments = StationMapWidget
            .moduleOverlaySegments(module, 200, 200, 0, 0, 0, 0, 0);
        int topLeftRightEdge = StationMapViewport.tileLeftX(0, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE - 1;
        int centerGapX = topLeftRightEdge + 1 + StationMapViewport.CONNECTOR_GAP / 2;
        int centerGapY = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE
            + StationMapViewport.CONNECTOR_GAP / 2;
        int yInsideTopTile = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE / 2;
        int topLeftBottomEdge = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE;
        int lowerLegTopEdge = StationMapViewport.tileTopY(1, 200, 0, 0);

        assertFalse(covers(segments, centerGapX, centerGapY));
        assertTrue(covers(segments, topLeftRightEdge, yInsideTopTile));
        assertTrue(covers(segments, topLeftRightEdge, topLeftBottomEdge));
        assertTrue(covers(segments, topLeftRightEdge, lowerLegTopEdge));
    }

    @Test
    void moduleFootprintHitTestIgnoresMissingCornerOfLShapedModule() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.CORE,
            ModuleShape.L_2x2,
            ModuleTier.EV);
        layout.place(module);

        int x = StationMapViewport.tileLeftX(1, 200, 0, 0, 0) + StationMapViewport.TILE_SIZE / 2;
        int y = StationMapViewport.tileTopY(0, 200, 0, 0) + StationMapViewport.TILE_SIZE / 2;

        assertNull(StationMapWidget.hitTestModuleFootprint(layout, x, y, 200, 200, 0, 0, 0, 0, 0));
    }

    private static boolean covers(List<ModuleFootprintProjection.Segment> segments, int x, int y) {
        for (ModuleFootprintProjection.Segment segment : segments) {
            if (segment.contains(x, y)) return true;
        }
        return false;
    }
}
