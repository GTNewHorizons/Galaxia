package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

final class PacketUtilTest {

    private enum TestEnum {
        FIRST,
        SECOND
    }

    @Test
    void readEnumCrashesForUnknownOrdinal() {
        var buf = Unpooled.buffer();
        buf.writeByte(99);

        assertThrows(IllegalStateException.class, () -> PacketUtil.readEnum(buf, TestEnum.class));
    }
}
