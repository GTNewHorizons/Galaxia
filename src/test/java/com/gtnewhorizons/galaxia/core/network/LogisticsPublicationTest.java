package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class LogisticsPublicationTest {

    private static final UUID TEAM = new UUID(1, 1);
    private static final UUID PLAYER = new UUID(2, 1);
    private static final CelestialAsset.ID SOURCE = new CelestialAsset.ID(new UUID(3, 1));
    private static final CelestialAsset.ID TARGET = new CelestialAsset.ID(new UUID(3, 2));
    private static final LogisticsDelivery.ID DELIVERY = new LogisticsDelivery.ID(new UUID(4, 1));
    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.MARS);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearStores() {
        LogisticStore.clearDeliveries();
        CelestialAssetStore.clear();
    }

    @Test
    void stableSignalsAndFlightCountdownPublishOnlyOncePerRecipient() {
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Server server = new AssetStateSync.Server(transport);
        for (int seconds = 0; seconds < 60; seconds++) {
            server.publishLogistics(PLAYER, TEAM, packet(512, 1, 1200 - seconds * 20));
        }
        assertEquals(1, transport.payloads.size());
        System.out.println(
            "STABLE bytes_before=" + transport.payloads.getFirst().length * 60
                + " bytes_after="
                + transport.payloads.getFirst().length
                + " packets=60->1");
        server.publishLogistics(new UUID(2, 2), TEAM, packet(512, 1, 20));
        assertEquals(2, transport.payloads.size());
    }

    @Test
    void changedCargoAndClearedStateArePublishedAndFailuresAreRetried() {
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Server server = new AssetStateSync.Server(transport);
        for (int seconds = 0; seconds < 60; seconds++) {
            server.publishLogistics(PLAYER, TEAM, packet(512, seconds / 10 + 1, 1200 - seconds * 20));
        }
        assertEquals(6, transport.payloads.size());
        System.out.println(
            "ACTIVE bytes_before=" + transport.payloads.getFirst().length * 60
                + " bytes_after="
                + transport.payloads.stream()
                    .mapToInt(p -> p.length)
                    .sum()
                + " packets=60->6");
        transport.failNext = true;
        LogisticsSyncPacket empty = LogisticsSyncPacket.from(List.of(), List.of());
        server.publishLogistics(PLAYER, TEAM, empty);
        assertEquals(6, transport.payloads.size());
        server.publishLogistics(PLAYER, TEAM, empty);
        assertEquals(7, transport.payloads.size());
        server.publishLogistics(PLAYER, TEAM, empty);
        assertEquals(7, transport.payloads.size());
    }

    @Test
    void cargoChangesDoNotRepublishUnchangedSignals() {
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Server server = new AssetStateSync.Server(transport);
        for (int seconds = 0; seconds < 60; seconds++) {
            server.publishLogistics(PLAYER, TEAM, packet(512, 1, seconds / 10 + 1, 1200 - seconds * 20));
        }
        assertEquals(6, transport.payloads.size());
        for (int i = 1; i < transport.payloads.size(); i++) {
            assertTrue(transport.payloads.get(i).length < transport.payloads.getFirst().length / 10);
        }
        System.out.println(
            "CARGO_ONLY bytes_before=" + transport.payloads.getFirst().length * 60
                + " bytes_after="
                + transport.payloads.stream()
                    .mapToInt(p -> p.length)
                    .sum()
                + " packets=60->6");
    }

    @Test
    void partialArrivalPublishesReducedCargoAndCompletionClearsTheDelivery() {
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Server server = new AssetStateSync.Server(transport);
        AutomatedFacility source = new AutomatedFacility(
            SOURCE,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        AutomatedFacility target = new AutomatedFacility(
            TARGET,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, target);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(Items.diamond));
        ItemStackWrapper cargo = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        target.insert(filler, 998);
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                DELIVERY,
                SOURCE,
                TARGET,
                cargo,
                5,
                1,
                LogisticSignal.Scope.SYSTEM,
                BODY,
                BODY,
                2,
                3));
        server.publishLogistics(PLAYER, TEAM, LogisticsSyncPacket.from(LogisticStore.activeDeliveries(), List.of()));
        LogisticStore.tickDeliveries();
        assertEquals(
            3,
            LogisticStore.activeDeliveries()
                .getFirst().data.amount());
        server.publishLogistics(PLAYER, TEAM, LogisticsSyncPacket.from(LogisticStore.activeDeliveries(), List.of()));
        assertEquals(2, transport.payloads.size());
        target.extract(filler, 3);
        LogisticStore.tickDeliveries();
        server.publishLogistics(PLAYER, TEAM, LogisticsSyncPacket.from(LogisticStore.activeDeliveries(), List.of()));
        assertEquals(3, transport.payloads.size());
        assertEquals(5, target.itemAmount(cargo));
    }

    @Test
    void reconnectAndTeamChangesReceiveStateEvenWhenContentMatches() {
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Server server = new AssetStateSync.Server(transport);
        LogisticsSyncPacket state = packet(1, 1, 20);
        server.publishLogistics(PLAYER, TEAM, state);
        server.resetRecipient(PLAYER);
        server.publishLogistics(PLAYER, TEAM, state);
        assertEquals(2, transport.payloads.size());
        server.publishLogistics(PLAYER, new UUID(1, 2), state);
        assertEquals(3, transport.payloads.size());
        server.forgetRecipient(PLAYER);
        server.publishLogistics(PLAYER, TEAM, state);
        assertEquals(4, transport.payloads.size());
    }

    private static LogisticsSyncPacket packet(int signalCount, long amount, int ticks) {
        return packet(signalCount, amount, amount, ticks);
    }

    private static LogisticsSyncPacket packet(int signalCount, long amount, long cargoAmount, int ticks) {
        ItemStackWrapper resource = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        List<LogisticSignal> signals = new ArrayList<>();
        for (int i = 0; i < signalCount; i++) signals.add(
            new LogisticSignal(
                new CelestialAsset.ID(new UUID(3, i)),
                BODY,
                resource,
                amount,
                LogisticSignal.Scope.SYSTEM,
                BODY,
                BODY));
        LogisticsDelivery delivery = LogisticsDelivery.createWithTrajectory(
            DELIVERY,
            SOURCE,
            TARGET,
            resource,
            cargoAmount,
            ticks,
            LogisticSignal.Scope.SYSTEM,
            BODY,
            BODY,
            2,
            3);
        return LogisticsSyncPacket.from(List.of(delivery), signals);
    }

    private static final class RecordingTransport implements AssetStateSync.ServerTransport {

        private final List<byte[]> payloads = new ArrayList<>();
        private boolean failNext;

        @Override
        public Collection<UUID> eligibleRecipients(UUID teamId) {
            return List.of(PLAYER);
        }

        @Override
        public void send(UUID recipientId, List<? extends IMessage> packets) {
            for (IMessage packet : packets) {
                if (!(packet instanceof LogisticsSyncPacket)) continue;
                if (failNext) {
                    failNext = false;
                    throw new IllegalStateException("Simulated transport failure");
                }
                ByteBuf buffer = Unpooled.buffer();
                try {
                    packet.toBytes(buffer);
                    byte[] bytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(bytes);
                    LogisticsSyncPacket decoded = new LogisticsSyncPacket();
                    ByteBuf input = Unpooled.wrappedBuffer(bytes);
                    ByteBuf reencoded = Unpooled.buffer();
                    try {
                        decoded.fromBytes(input);
                        decoded.toBytes(reencoded);
                        byte[] roundTrip = new byte[reencoded.readableBytes()];
                        reencoded.readBytes(roundTrip);
                        assertArrayEquals(bytes, roundTrip);
                    } finally {
                        input.release();
                        reencoded.release();
                    }
                    payloads.add(bytes);
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
