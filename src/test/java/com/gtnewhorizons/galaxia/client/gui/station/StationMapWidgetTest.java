package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
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
}
