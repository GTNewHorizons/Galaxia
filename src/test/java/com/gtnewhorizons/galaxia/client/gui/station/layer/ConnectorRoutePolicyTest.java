package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class ConnectorRoutePolicyTest {

    @BeforeAll
    static void initModules() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void moduleConnectorOnlyJoinsDifferentModules() {
        ModuleInstance lShape = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.CORE, ModuleShape.L_2x2, ModuleTier.EV);
        PlacedTile firstTile = tile(lShape);
        PlacedTile secondTile = tile(lShape);
        PlacedTile neighbourTile = tile(
            FacilityModuleKind.STORAGE.create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV));

        assertFalse(ConnectorRoutePolicy.hasModuleConnector(firstTile, secondTile));
        assertTrue(ConnectorRoutePolicy.hasModuleConnector(firstTile, neighbourTile));
    }

    @Test
    void capacityConnectorOnlyJoinsDifferentModulesOfTheSameCapacityKind() {
        PlacedTile storageA = tile(
            FacilityModuleKind.STORAGE.create(StationTileCoord.CORE, ModuleShape.SINGLE, ModuleTier.HV));
        PlacedTile storageB = tile(
            FacilityModuleKind.STORAGE.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV));
        PlacedTile tank = tile(
            FacilityModuleKind.TANK.create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.HV));
        PlacedTile hammer = tile(
            FacilityModuleKind.HAMMER.create(StationTileCoord.of(3, 0), ModuleShape.SINGLE, ModuleTier.EV));

        assertEquals(FacilityModuleKind.STORAGE, ConnectorRoutePolicy.capacityConnectorKind(storageA, storageB));
        assertNull(ConnectorRoutePolicy.capacityConnectorKind(storageA, tank));
        assertNull(ConnectorRoutePolicy.capacityConnectorKind(storageA, hammer));
        assertNull(ConnectorRoutePolicy.capacityConnectorKind(storageA, storageA));
    }

    private static PlacedTile tile(ModuleInstance module) {
        return new PlacedTile(module, StationTileState.OCCUPIED_OPERATIONAL);
    }
}
