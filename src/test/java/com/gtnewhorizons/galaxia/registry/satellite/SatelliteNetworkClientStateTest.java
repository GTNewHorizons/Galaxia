package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

final class SatelliteNetworkClientStateTest {

    @AfterEach
    void clear() {
        SatelliteNetworkClientState.clear();
    }

    @Test
    void updateKeepsNewestSnapshotByRevision() {
        SatelliteNetworkState newer = state(2, 10L);
        SatelliteNetworkState older = state(1, 20L);

        SatelliteNetworkClientState.update(newer);
        SatelliteNetworkClientState.update(older);

        assertEquals(
            2,
            SatelliteNetworkClientState.current()
                .revision());
        assertEquals(
            10L,
            SatelliteNetworkClientState.current()
                .capacityKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void updateAcceptsSnapshotForDifferentTeamEvenWhenRevisionIsLower() {
        UUID firstTeam = new UUID(1L, 2L);
        UUID nextTeam = new UUID(3L, 4L);

        SatelliteNetworkClientState.update(state(firstTeam, 5, 10L));
        SatelliteNetworkClientState.update(state(nextTeam, 1, 20L));

        assertEquals(
            nextTeam,
            SatelliteNetworkClientState.current()
                .teamId());
        assertEquals(
            20L,
            SatelliteNetworkClientState.current()
                .capacityKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void assetSyncClearClearsSatelliteSnapshot() {
        SatelliteNetworkClientState.update(state(new UUID(1L, 2L), 1, 10L));

        AssetSyncPacket.Handler.handleClientSync(AssetSyncPacket.clear());

        assertEquals(
            0,
            SatelliteNetworkClientState.current()
                .revision());
        assertEquals(
            0L,
            SatelliteNetworkClientState.current()
                .capacityKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    private static SatelliteNetworkState state(int revision, long capacityKbps) {
        return state(new UUID(1L, 2L), revision, capacityKbps);
    }

    private static SatelliteNetworkState state(UUID teamId, int revision, long capacityKbps) {
        return new SatelliteNetworkState(
            teamId,
            revision,
            Map.of(
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                new SatelliteNetworkState.Body(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    capacityKbps,
                    0L)),
            java.util.List.of());
    }
}
