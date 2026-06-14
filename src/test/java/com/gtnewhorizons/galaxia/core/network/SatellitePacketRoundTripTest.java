package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.PlanetarySatelliteStore;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SatellitePacketRoundTripTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000234");

    @Test
    void syncPacketRoundTripsSatelliteRows() {
        PlanetarySatelliteStore source = new PlanetarySatelliteStore();
        source.set(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        source.set(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        source.set(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION, 5);

        SatelliteSyncPacket original = SatelliteSyncPacket.fullForTeam(TEAM, source.snapshotTeam(TEAM));

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        SatelliteSyncPacket decoded = new SatelliteSyncPacket();
        decoded.fromBytes(buf);

        PlanetarySatelliteStore target = new PlanetarySatelliteStore();
        decoded.applyTo(target);

        assertEquals(3, target.count(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(2, target.count(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
        assertEquals(5, target.count(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION));
    }
}
