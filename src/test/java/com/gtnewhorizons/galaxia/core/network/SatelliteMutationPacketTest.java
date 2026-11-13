package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.network.SatelliteMutationPacket.SatelliteMutationOperation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SatelliteMutationPacketTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.MARS);
    private static final CelestialServerRuntime RUNTIME = CelestialServerRuntime.create();

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    @AfterEach
    void clear() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        RUNTIME.reset();
    }

    @Test
    void networkCommandsMutateOnlyTheRequestedTeamAndSatelliteKindWithoutGuiState() {
        UUID otherTeam = UUID.randomUUID();
        new SatelliteMutationPacket(otherTeam, BODY, SatelliteKind.COMMUNICATION, SatelliteMutationOperation.SET, 4)
            .apply();

        decoded(SatelliteMutationOperation.SET, 3).apply();
        assertCount(TEAM, 3);
        decoded(SatelliteMutationOperation.DELETE_AMOUNT, 1).apply();
        assertCount(TEAM, 2);
        decoded(SatelliteMutationOperation.ADD, 1).apply();
        assertCount(TEAM, 3);
        decoded(SatelliteMutationOperation.DELETE_ALL, 0).apply();
        assertCount(TEAM, 0);
        assertCount(otherTeam, 4);
    }

    @Test
    void invalidAddDoesNotChangeSatelliteState() {
        decoded(SatelliteMutationOperation.SET, 2).apply();
        assertThrows(IllegalArgumentException.class, () -> decoded(SatelliteMutationOperation.ADD, 0).apply());
        assertCount(TEAM, 2);
    }

    private static SatelliteMutationPacket decoded(SatelliteMutationOperation operation, int amount) {
        ByteBuf bytes = Unpooled.buffer();
        try {
            new SatelliteMutationPacket(TEAM, BODY, SatelliteKind.COMMUNICATION, operation, amount).toBytes(bytes);
            SatelliteMutationPacket decoded = new SatelliteMutationPacket();
            decoded.fromBytes(bytes);
            return decoded;
        } finally {
            bytes.release();
        }
    }

    private static void assertCount(UUID team, int expected) {
        assertEquals(expected, CelestialAssetStore.SERVER.satelliteCount(team, BODY, SatelliteKind.COMMUNICATION));
    }
}
