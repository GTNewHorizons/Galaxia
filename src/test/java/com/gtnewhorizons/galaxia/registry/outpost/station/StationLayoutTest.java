package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleRegistry;

final class StationLayoutTest {

    @BeforeAll
    static void initModules() {
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
}
