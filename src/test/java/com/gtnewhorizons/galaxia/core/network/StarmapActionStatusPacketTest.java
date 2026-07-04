package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

final class StarmapActionStatusPacketTest {

    @Test
    void roundTripsServerRejectionMessage() {
        StarmapActionStatusPacket packet = StarmapActionStatusPacket
            .rejected("Cannot create asset on hidden asteroid FROZEN_BELT");
        StarmapActionStatusPacket decoded = new StarmapActionStatusPacket();

        var buf = Unpooled.buffer();
        packet.toBytes(buf);
        decoded.fromBytes(buf);

        assertEquals("Cannot create asset on hidden asteroid FROZEN_BELT", decoded.message());
    }
}
