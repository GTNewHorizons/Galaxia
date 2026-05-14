package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.core.profiling.HammerTrajectoryLoadSample;

import io.netty.buffer.Unpooled;

final class ProfilerSyncPacketTest {

    @BeforeEach
    void resetClientState() {
        CelestialClient.clear();
    }

    @Test
    void hammerTrajectoryLoadRoundTripUpdatesClientSample() {
        ProfilerSyncPacket packet = ProfilerSyncPacket.hammerTrajectoryLoad(1.25, 2.5);
        var buf = Unpooled.buffer();
        packet.toBytes(buf);

        ProfilerSyncPacket decoded = new ProfilerSyncPacket();
        decoded.fromBytes(buf);
        ProfilerSyncPacket.applyClient(decoded);

        HammerTrajectoryLoadSample sample = CelestialClient.hammerTrajectoryLoadSample();
        assertEquals(1.25, sample.ownMsPerTick(), 1e-9);
        assertEquals(2.5, sample.allMsPerTick(), 1e-9);
    }
}
