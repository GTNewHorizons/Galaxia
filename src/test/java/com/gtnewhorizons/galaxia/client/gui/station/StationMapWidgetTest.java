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

    private static final StationMapFrame FRAME = new StationMapFrame(200, 200, 0, 0, 0, 0, 0);

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

            assertTrue(tiles.containsKey(StationMapOverlayPainter.alertBadgeCoord(module, tiles)));
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

        int x = FRAME.tileLocalX(0) + StationMapFrame.TILE_SIZE / 2;
        int y = FRAME.tileLocalY(0) + StationMapFrame.TILE_SIZE + StationMapFrame.CONNECTOR_GAP / 2;

        assertEquals(StationTileCoord.CORE, StationMapHitTester.hitTestModuleFootprint(layout, x, y, FRAME));
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

        int x = FRAME.tileLocalX(0) + StationMapFrame.TILE_SIZE + StationMapFrame.CONNECTOR_GAP / 2;
        int y = FRAME.tileLocalY(0) + StationMapFrame.TILE_SIZE + StationMapFrame.CONNECTOR_GAP / 2;

        assertEquals(StationTileCoord.CORE, StationMapHitTester.hitTestModuleFootprint(layout, x, y, FRAME));
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

        int x = FRAME.tileLocalX(0) + StationMapFrame.TILE_SIZE / 2;
        int y = FRAME.tileLocalY(1) + StationMapFrame.TILE_SIZE / 2;

        assertEquals(StationTileCoord.CORE, StationMapHitTester.hitTestModuleFootprint(layout, x, y, FRAME));
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

        int x = FRAME.tileLocalX(1) + StationMapFrame.TILE_SIZE / 2;
        int y = FRAME.tileLocalY(0) + StationMapFrame.TILE_SIZE / 2;

        assertNull(StationMapHitTester.hitTestModuleFootprint(layout, x, y, FRAME));
    }

    @Test
    void maintenanceCoverageTargetsWholeAdjacentModulesAndEmptyTiles() {
        ModuleInstance maintenanceBay = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MAINTENANCE_BAY,
            StationTileCoord.CORE,
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        ModuleInstance macerator = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(1, 0),
            ModuleShape.L_2x2,
            ModuleTier.EV);
        Map<StationTileCoord, PlacedTile> tiles = new LinkedHashMap<>();
        tiles.put(StationTileCoord.CORE, new PlacedTile(maintenanceBay, StationTileState.OCCUPIED_OPERATIONAL));
        for (StationTileCoord tile : macerator.tiles()) {
            tiles.put(tile, new PlacedTile(macerator, StationTileState.OCCUPIED_OPERATIONAL));
        }

        List<StationMapOverlayPainter.MaintenanceCoverageTarget> targets = StationMapOverlayPainter
            .maintenanceCoverageTargets(StationTileCoord.CORE, tiles);

        assertEquals(
            1,
            targets.stream()
                .filter(target -> target.module() == macerator)
                .count());
        assertTrue(
            targets.stream()
                .anyMatch(
                    target -> target.tile() != null && target.tile()
                        .equals(StationTileCoord.of(-1, 0))));
        assertFalse(
            targets.stream()
                .anyMatch(
                    target -> target.tile() != null && target.tile()
                        .equals(StationTileCoord.of(1, 0))));
    }

    @Test
    void maintenanceCoverageFillsWholeAdjacentModuleFootprint() {
        ModuleInstance maintenanceBay = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MAINTENANCE_BAY,
            StationTileCoord.CORE,
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        ModuleInstance macerator = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(1, 0),
            ModuleShape.L_2x2,
            ModuleTier.EV);
        Map<StationTileCoord, PlacedTile> tiles = new LinkedHashMap<>();
        tiles.put(StationTileCoord.CORE, new PlacedTile(maintenanceBay, StationTileState.OCCUPIED_OPERATIONAL));
        for (StationTileCoord tile : macerator.tiles()) {
            tiles.put(tile, new PlacedTile(macerator, StationTileState.OCCUPIED_OPERATIONAL));
        }
        StationMapFrame frame = new StationMapFrame(240, 240, 0, 0, 0, 0, 0);

        List<ModuleFootprintProjection.Segment> segments = StationMapOverlayPainter
            .maintenanceCoverageFillSegments(StationTileCoord.CORE, tiles, frame);
        int x = frame.tileLocalX(1) + StationMapFrame.TILE_SIZE / 2;
        int y = frame.tileLocalY(0) + StationMapFrame.TILE_SIZE / 2;

        assertTrue(covers(segments, x, y));
    }

    private static boolean covers(List<ModuleFootprintProjection.Segment> segments, int x, int y) {
        for (ModuleFootprintProjection.Segment segment : segments) {
            if (segment.contains(x, y)) return true;
        }
        return false;
    }
}
