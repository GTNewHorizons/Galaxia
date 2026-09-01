package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;

import net.minecraft.init.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class LogisticsSyncPacketTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void sourceSignalPayloadReencodesAfterRoundTrip() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        LogisticSignal signal = new LogisticSignal(
            facility.assetId,
            facility.systemKey,
            resource,
            -12L,
            LogisticSignal.Scope.SYSTEM,
            facility.celestialObjectKey,
            facility.planetaryAnchorBodyKey);
        LogisticsSyncPacket encoded = LogisticsSyncPacket.from(List.of(), List.of(signal));
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);
        byte[] firstEncoding = bytes(buffer);
        LogisticsSyncPacket decoded = new LogisticsSyncPacket();
        decoded.fromBytes(Unpooled.wrappedBuffer(firstEncoding));
        ByteBuf reencoded = Unpooled.buffer();

        decoded.toBytes(reencoded);

        assertArrayEquals(firstEncoding, bytes(reencoded));
    }

    private static byte[] bytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }
}
