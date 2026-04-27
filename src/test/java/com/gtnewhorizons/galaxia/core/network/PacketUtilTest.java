package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

final class PacketUtilTest {

    private enum TestEnum {
        FIRST,
        SECOND
    }

    @Test
    void readEnumFallsBackToFirstValueForUnknownOrdinal() {
        var buf = Unpooled.buffer();
        buf.writeByte(99);

        assertEquals(TestEnum.FIRST, PacketUtil.readEnum(buf, TestEnum.class));
    }

    @Test
    void fromOrdinalOrNullReturnsNullForUnknownOrdinal() {
        assertNull(PacketUtil.fromOrdinalOrNull(99, TestEnum.class));
    }
}
