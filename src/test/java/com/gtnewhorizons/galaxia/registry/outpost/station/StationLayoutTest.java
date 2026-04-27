package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleRegistry;

final class StationLayoutTest {

    @BeforeAll
    static void initModules() {
        CelestialRegistry.freezeAndBake();
        OutpostModuleRegistry.init();
    }

    @Test
    void removeTileForModuleRemovesOnlyMatchingModuleTile() {
        StationLayout layout = new StationLayout();
        ModuleInstance module = FacilityModuleKind.POWER.createInstance();
        StationTileCoord coord = StationTileCoord.of(1, 0);
        layout.place(coord, new PlacedTile(module, StationTileState.UNDER_CONSTRUCTION));

        layout.removeTileForModule(module.id);

        assertFalse(layout.isOccupied(coord));
        assertTrue(layout.isOccupied(StationTileCoord.CORE));
    }

    @Test
    void deconstructThenRebuildElsewhereKeepsUnrelatedTiles() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        StationLayout layout = station.stationLayout();
        assertNotNull(layout);

        ModuleInstance removed = FacilityModuleKind.POWER.createInstance();
        ModuleInstance retained = FacilityModuleKind.MINER.createInstance();
        station.addModule(removed);
        station.addModule(retained);
        StationTileCoord removedCoord = StationTileCoord.of(1, 0);
        StationTileCoord retainedCoord = StationTileCoord.of(0, 1);
        layout.place(removedCoord, new PlacedTile(removed, StationTileState.OCCUPIED_OPERATIONAL));
        layout.place(retainedCoord, new PlacedTile(retained, StationTileState.OCCUPIED_OPERATIONAL));

        assertTrue(station.removeModule(removed.id));

        assertFalse(layout.isOccupied(removedCoord));
        assertTrue(layout.isOccupied(retainedCoord));
        assertTrue(layout.isOccupied(StationTileCoord.CORE));
        assertEquals(
            1,
            station.modules()
                .size());

        ModuleInstance rebuilt = FacilityModuleKind.HAMMER.createInstance();
        StationTileCoord rebuiltCoord = StationTileCoord.of(-1, 0);
        station.addModule(rebuilt);
        layout.place(rebuiltCoord, new PlacedTile(rebuilt, StationTileState.UNDER_CONSTRUCTION));

        assertTrue(layout.isOccupied(rebuiltCoord));
        assertTrue(layout.isOccupied(retainedCoord));
        assertTrue(layout.isOccupied(StationTileCoord.CORE));
        assertEquals(
            2,
            station.modules()
                .size());
    }
}
