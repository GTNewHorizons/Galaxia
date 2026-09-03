package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class LogisticsSyncPacketTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void taggedItemIdentitySurvivesDeliveryAndSignalRoundTrip() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAsset.ID destinationId = CelestialAsset.ID.create();
        LogisticsDelivery.ID deliveryId = LogisticsDelivery.ID.create();
        ItemStack taggedStack = new ItemStack(Items.iron_ingot);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("identity", "tagged");
        taggedStack.setTagCompound(tag);
        ItemStackWrapper tagged = ItemStackWrapper.of(taggedStack);
        ItemStackWrapper plain = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));

        assertIdentitySurvivesRoundTrip(facility, destinationId, deliveryId, tagged, plain, true);
        assertIdentitySurvivesRoundTrip(facility, destinationId, deliveryId, tagged, plain, false);
    }

    private static void assertIdentitySurvivesRoundTrip(AutomatedFacility facility, CelestialAsset.ID destinationId,
        LogisticsDelivery.ID deliveryId, ItemStackWrapper tagged, ItemStackWrapper plain, boolean delivery) {
        byte[] firstEncoding = encode(packet(facility, destinationId, deliveryId, tagged, delivery));
        byte[] roundTripEncoding = reencode(firstEncoding);
        byte[] plainEncoding = encode(packet(facility, destinationId, deliveryId, plain, delivery));

        assertArrayEquals(firstEncoding, roundTripEncoding);
        assertFalse(Arrays.equals(plainEncoding, roundTripEncoding));
    }

    private static LogisticsSyncPacket packet(AutomatedFacility facility, CelestialAsset.ID destinationId,
        LogisticsDelivery.ID deliveryId, ItemStackWrapper resource, boolean delivery) {
        if (delivery) {
            LogisticsDelivery transfer = LogisticsDelivery.createWithTrajectory(
                deliveryId,
                facility.assetId,
                destinationId,
                resource,
                12L,
                30,
                LogisticSignal.Scope.SYSTEM,
                facility.celestialObjectKey,
                facility.celestialObjectKey,
                2.0,
                3.0);
            return LogisticsSyncPacket.from(List.of(transfer), List.of());
        }
        LogisticSignal signal = new LogisticSignal(
            facility.assetId,
            facility.systemKey,
            resource,
            -12L,
            LogisticSignal.Scope.SYSTEM,
            facility.celestialObjectKey,
            facility.planetaryAnchorBodyKey);
        return LogisticsSyncPacket.from(List.of(), List.of(signal));
    }

    private static byte[] encode(LogisticsSyncPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return bytes(buffer);
    }

    private static byte[] reencode(byte[] firstEncoding) {
        LogisticsSyncPacket decoded = new LogisticsSyncPacket();
        decoded.fromBytes(Unpooled.wrappedBuffer(firstEncoding));
        ByteBuf reencoded = Unpooled.buffer();
        decoded.toBytes(reencoded);
        return bytes(reencoded);
    }

    private static byte[] bytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }
}
