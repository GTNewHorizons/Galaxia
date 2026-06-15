package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SatellitePacketRoundTripTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000234");

    @Test
    void syncPacketRoundTripsSatelliteCounts() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION, 5);

        SatelliteSyncPacket original = SatelliteSyncPacket
            .fullForTeam(TEAM, CelestialAssetStore.SERVER.snapshotSatelliteCounts(TEAM));

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        SatelliteSyncPacket decoded = new SatelliteSyncPacket();
        decoded.fromBytes(buf);

        decoded.applyTo(CelestialAssetStore.CLIENT);

        assertEquals(
            3,
            CelestialAssetStore.CLIENT.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(
            2,
            CelestialAssetStore.CLIENT.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
        assertEquals(
            5,
            CelestialAssetStore.CLIENT.satelliteCount(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION));
    }
}
