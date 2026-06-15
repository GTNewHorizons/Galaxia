package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SatelliteDebugMutationPacketTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000345");

    @Test
    void setAddAndDeleteAllMutateSelectedKindOnly() {
        CelestialAssetStore.SERVER.clearInternal();

        SatelliteDebugMutationPacket.set(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 4)
            .apply(CelestialAssetStore.SERVER);
        SatelliteDebugMutationPacket.add(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 2)
            .apply(CelestialAssetStore.SERVER);
        SatelliteDebugMutationPacket.set(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 7)
            .apply(CelestialAssetStore.SERVER);
        SatelliteDebugMutationPacket.deleteAll(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION)
            .apply(CelestialAssetStore.SERVER);

        assertEquals(
            0,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(
            7,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
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

        CelestialAssetStore.SERVER.clearInternal();
        decoded.apply(CelestialAssetStore.SERVER);

        assertEquals(
            9,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MOON, SatelliteKind.PROSPECTING));
    }
}
