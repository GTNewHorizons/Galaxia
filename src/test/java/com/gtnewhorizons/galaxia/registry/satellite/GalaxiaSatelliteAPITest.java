package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaSatelliteAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class GalaxiaSatelliteAPITest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000108");

    @AfterEach
    void clearState() {
        SatelliteNetworkService.clear();
    }

    @Test
    void exposesReadOnlyNetworkBandwidthAndPendingDataByBody() {
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteNetworkService.dataBuffers()
            .finishProduction(TEAM, CelestialObjectId.MARS, prospecting, SatelliteBandwidthFormatter.kilobits(15L));
        SatelliteNetworkService.dataBuffers()
            .requestData(TEAM, CelestialObjectId.OVERWORLD, prospecting, SatelliteBandwidthFormatter.kilobits(15L));
        SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            Map.of(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L),
            SatelliteNetworkService.dataBuffers());

        assertEquals(10L, GalaxiaSatelliteAPI.localCapacityKbps(TEAM, CelestialObjectId.MARS));
        assertEquals(10L, GalaxiaSatelliteAPI.localUsedKbps(TEAM, CelestialObjectId.MARS));
        assertEquals(
            10L,
            GalaxiaSatelliteAPI.pathCapacityKbps(TEAM, CelestialObjectId.MARS, CelestialObjectId.OVERWORLD));
        assertFalse(GalaxiaSatelliteAPI.canStartProcess(TEAM, CelestialObjectId.MARS, prospecting));

        List<GalaxiaSatelliteAPI.PendingData> pending = GalaxiaSatelliteAPI.pendingData(TEAM, CelestialObjectId.MARS);

        assertEquals(1, pending.size());
        assertEquals(
            prospecting,
            pending.get(0)
                .key());
        assertEquals(
            SatelliteBandwidthFormatter.kilobits(15L),
            pending.get(0)
                .deciKb());
    }

    @Test
    void processCanStartWhenBodyBufferIsWithinLocalCapacity() {
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            Map.of(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L),
            SatelliteNetworkService.dataBuffers());

        assertTrue(GalaxiaSatelliteAPI.canStartProcess(TEAM, CelestialObjectId.MARS, prospecting));
    }

    private static List<SatelliteNetworkGraph.Node> nodes() {
        return List.of(node(CelestialObjectId.MARS, 0.0D), node(CelestialObjectId.OVERWORLD, 10.0D));
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x) {
        return new SatelliteNetworkGraph.Node(id, null, id.ordinal(), x, 0.0D, 1.0D);
    }
}
