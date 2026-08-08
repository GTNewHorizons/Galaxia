package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ConnectionLayerRendererTest {

    @BeforeAll
    static void initModules() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void connectorsOnlyJoinDifferentModules() {
        ModuleInstance lShape = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.CORE, ModuleShape.L_2x2, ModuleTier.EV);
        PlacedTile firstTile = new PlacedTile(lShape, StationTileState.OCCUPIED_OPERATIONAL);
        PlacedTile secondTile = new PlacedTile(lShape, StationTileState.OCCUPIED_OPERATIONAL);
        ModuleInstance neighbour = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV);
        PlacedTile neighbourTile = new PlacedTile(neighbour, StationTileState.OCCUPIED_OPERATIONAL);

        assertFalse(ConnectionLayerRenderer.shouldDrawConnectorBetween(firstTile, secondTile));
        assertTrue(ConnectionLayerRenderer.shouldDrawConnectorBetween(firstTile, neighbourTile));
    }
}
