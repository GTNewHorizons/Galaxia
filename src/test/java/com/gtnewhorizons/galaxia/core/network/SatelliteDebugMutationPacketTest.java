package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.PlanetarySatelliteStore;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SatelliteDebugMutationPacketTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000345");

    @Test
    void setAddAndDeleteAllMutateSelectedKindOnly() {
        PlanetarySatelliteStore store = new PlanetarySatelliteStore();

        SatelliteDebugMutationPacket.set(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 4)
            .apply(store);
        SatelliteDebugMutationPacket.add(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 2)
            .apply(store);
        SatelliteDebugMutationPacket.set(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 7)
            .apply(store);
        SatelliteDebugMutationPacket.deleteAll(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION)
            .apply(store);

        assertEquals(0, store.count(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(7, store.count(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
    }

    @Test
    void debugMutationsRequireOpAndCreative() {
        assertFalse(SatelliteDebugMutationPacket.isAuthorized(false, false));
        assertFalse(SatelliteDebugMutationPacket.isAuthorized(false, true));
        assertFalse(SatelliteDebugMutationPacket.isAuthorized(true, false));
        assertTrue(SatelliteDebugMutationPacket.isAuthorized(true, true));
    }

    @Test
    void mutationPacketRoundTripsAndApplies() {
        SatelliteDebugMutationPacket original = SatelliteDebugMutationPacket
            .set(TEAM, CelestialObjectId.MOON, SatelliteKind.PROSPECTING, 9);

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        SatelliteDebugMutationPacket decoded = new SatelliteDebugMutationPacket();
        decoded.fromBytes(buf);

        PlanetarySatelliteStore store = new PlanetarySatelliteStore();
        decoded.apply(store);

        assertEquals(9, store.count(TEAM, CelestialObjectId.MOON, SatelliteKind.PROSPECTING));
    }
}
