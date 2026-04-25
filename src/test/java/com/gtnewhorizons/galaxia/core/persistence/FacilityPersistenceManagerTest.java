package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

final class FacilityPersistenceManagerTest {

    private static final Gson GSON = new Gson();

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        OutpostModuleRegistry.init();
    }

    @Test
    void facilityPersistenceRoundTripsFullStationLayout() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = createStationWithFullLayout();

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = new AutomatedFacility(
            station.assetId,
            station.celestialObjectId,
            station.kind,
            station.status());

        manager.decodeFacilityState(decoded, encoded);

        assertEquals(station.getEnergyStored(), decoded.getEnergyStored());
        assertEquals(
            station.modules()
                .size(),
            decoded.modules()
                .size());
        assertLayoutEquals(station.stationLayout(), decoded.stationLayout());
        assertEquals(GSON.toJson(encoded), GSON.toJson(manager.encodeFacilityState(decoded)));
    }

    private static AutomatedFacility createStationWithFullLayout() {
        AutomatedFacility station = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        station.setEnergyStored(245_760L);

        ModuleInstance hammer = addModule(station, FacilityModuleKind.HAMMER, Buildable.Status.OPERATIONAL);
        ModuleInstance miner = addModule(station, FacilityModuleKind.MINER, Buildable.Status.DISABLED);
        ModuleInstance power = addModule(station, FacilityModuleKind.POWER, Buildable.Status.IN_CONSTRUCTION);
        ModuleInstance bigHammer = addModule(station, FacilityModuleKind.BIG_HAMMER, Buildable.Status.DECONSTRUCTION);

        StationLayout layout = station.stationLayout();
        assertNotNull(layout);
        layout.place(StationTileCoord.of(1, 0), new PlacedTile(hammer, StationTileState.OCCUPIED_OPERATIONAL));
        layout.place(StationTileCoord.of(2, 0), new PlacedTile(miner, StationTileState.OCCUPIED_DISABLED));
        layout.place(StationTileCoord.of(2, 1), new PlacedTile(power, StationTileState.UNDER_CONSTRUCTION));
        layout.place(StationTileCoord.of(1, 1), new PlacedTile(bigHammer, StationTileState.UNDER_DECONSTRUCTION));
        return station;
    }

    private static ModuleInstance addModule(AutomatedFacility station, FacilityModuleKind kind,
        Buildable.Status status) {
        ModuleInstance module = kind.createInstance();
        module.updateStatus(status);
        module.setEnergyBuffer(
            64L + station.modules()
                .size());
        station.addModule(module);
        return module;
    }

    private static void assertLayoutEquals(StationLayout expected, StationLayout actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());

        for (Map.Entry<StationTileCoord, PlacedTile> entry : expected.snapshot()
            .entrySet()) {
            PlacedTile expectedTile = entry.getValue();
            PlacedTile actualTile = actual.get(entry.getKey());
            assertNotNull(actualTile);
            assertEquals(expectedTile.state(), actualTile.state());
            if (expectedTile.module() == null) {
                assertNull(actualTile.module());
            } else {
                assertNotNull(actualTile.module());
                assertEquals(expectedTile.module().id, actualTile.module().id);
                assertEquals(
                    expectedTile.module()
                        .kind(),
                    actualTile.module()
                        .kind());
            }
        }
    }

}
