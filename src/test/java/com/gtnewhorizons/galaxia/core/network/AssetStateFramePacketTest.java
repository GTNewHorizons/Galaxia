package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class AssetStateFramePacketTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @BeforeEach
    void clearStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void largeUpdateUsesBoundedFramesAndAppliesOnlyAfterCompleteAssembly() {
        AutomatedFacility facility = facility();
        facility.setDisplayName("x".repeat(62_000));
        AssetSyncPacket update = AssetSyncPacket.fullSync(facility)
            .withPublishedRevision(0L, 1L);
        List<AssetStateFramePacket> frames = AssetStateSync.Server.frame(update);
        frames = frames.stream()
            .map(AssetStateFramePacketTest::roundTrip)
            .toList();

        assertTrue(frames.size() > 1);
        for (AssetStateFramePacket frame : frames) {
            ByteBuf body = Unpooled.buffer();
            frame.toBytes(body);
            assertTrue(body.readableBytes() <= AssetStateFramePacket.MAX_MESSAGE_BODY_BYTES);
        }

        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);
        List<AssetStateFramePacket> reversed = new ArrayList<>(frames);
        Collections.reverse(reversed);
        for (int i = 0; i < reversed.size() - 1; i++) client.receive(reversed.get(i));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));

        client.receive(reversed.get(reversed.size() - 1));

        AutomatedFacility applied = (AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId);
        assertEquals(facility.displayName(), applied.displayName());
        assertTrue(transport.fullRequests.isEmpty());
    }

    @Test
    void identicalDuplicateIsIdempotentButConflictingDuplicateDropsAssemblyAndRequestsOnce() {
        AutomatedFacility facility = facility();
        facility.setDisplayName("x".repeat(62_000));
        List<AssetStateFramePacket> frames = AssetStateSync.Server.frame(
            AssetSyncPacket.fullSync(facility)
                .withPublishedRevision(0L, 1L));
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        client.receive(frames.get(0));
        client.receive(frames.get(0));
        byte[] conflictingPayload = frames.get(0)
            .payload();
        conflictingPayload[0] ^= 1;
        AssetStateFramePacket conflicting = withPayload(frames.get(0), conflictingPayload);
        client.receive(conflicting);
        client.receive(conflicting);
        for (int i = 1; i < frames.size(); i++) client.receive(frames.get(i));

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void malformedAndExpiredAssembliesRequestRateLimitedRecoveryWithoutMutation() {
        AutomatedFacility malformedFacility = facility();
        AssetStateFramePacket valid = AssetStateSync.Server.frame(
            AssetSyncPacket.fullSync(malformedFacility)
                .withPublishedRevision(0L, 1L))
            .get(0);
        RecordingClientTransport transport = new RecordingClientTransport();
        AtomicLong now = new AtomicLong();
        AssetStateSync.Client client = new AssetStateSync.Client(transport, now::get);

        client.receive(withFrameIndex(valid, valid.frameCount()));
        client.receive(withFrameIndex(valid, valid.frameCount()));

        AutomatedFacility expiredFacility = facility();
        expiredFacility.setDisplayName("x".repeat(62_000));
        List<AssetStateFramePacket> expiring = AssetStateSync.Server.frame(
            AssetSyncPacket.fullSync(expiredFacility)
                .withPublishedRevision(0L, 1L));
        client.receive(expiring.get(0));
        now.addAndGet(AssetStateSync.Client.ASSEMBLY_TIMEOUT_MILLIS + 1L);
        client.tick();

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(malformedFacility.assetId));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(expiredFacility.assetId));
        assertEquals(List.of(malformedFacility.assetId, expiredFacility.assetId), transport.fullRequests);
    }

    @Test
    void logicalDecoderRejectsImpossibleFullStateDeltaCountWithoutAllocation() {
        AutomatedFacility facility = facility();
        AssetSyncPacket update = AssetSyncPacket.fullSync(facility)
            .withPublishedRevision(0L, 1L);
        update.fullSyncDeltas()
            .clear();
        AssetStateFramePacket valid = AssetStateSync.Server.frame(update)
            .get(0);
        byte[] payload = valid.payload();
        int countOffset = payload.length - Integer.BYTES;
        payload[countOffset] = 0x7f;
        payload[countOffset + 1] = (byte) 0xff;
        payload[countOffset + 2] = (byte) 0xff;
        payload[countOffset + 3] = (byte) 0xff;
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        client.receive(withPayload(valid, payload));

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void logicalDecoderCapsFullStateObjectCountEvenWhenPayloadHasEnoughBytes() {
        AutomatedFacility facility = facility();
        AssetSyncPacket update = AssetSyncPacket.fullSync(facility)
            .withPublishedRevision(0L, 1L);
        update.fullSyncDeltas()
            .clear();
        AssetStateFramePacket valid = AssetStateSync.Server.frame(update)
            .get(0);
        byte[] encoded = valid.payload();
        int countOffset = encoded.length - Integer.BYTES;
        int rejectedCount = AssetSyncPacket.MAX_FULL_SYNC_DELTAS + 1;
        byte[] oversized = Arrays.copyOf(encoded, encoded.length + rejectedCount);
        oversized[countOffset] = (byte) (rejectedCount >>> 24);
        oversized[countOffset + 1] = (byte) (rejectedCount >>> 16);
        oversized[countOffset + 2] = (byte) (rejectedCount >>> 8);
        oversized[countOffset + 3] = (byte) rejectedCount;
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : framesForPayload(facility.assetId, oversized)) client.receive(frame);

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void logicalDecoderRejectsUnknownNestedDeltaWithoutApplyingPartialState() {
        AutomatedFacility facility = facility();
        AssetSyncPacket update = AssetSyncPacket.fullSync(facility)
            .withPublishedRevision(0L, 1L);
        update.fullSyncDeltas()
            .clear();
        update.fullSyncDeltas()
            .add(AssetSyncPacket.filterUpdated(facility.assetId, true, List.of()));
        AssetStateFramePacket valid = AssetStateSync.Server.frame(update)
            .get(0);
        byte[] payload = valid.payload();
        payload[payload.length - 4] = Byte.MAX_VALUE;
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        client.receive(withPayload(valid, payload));

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void clearAndAssetRemovalDiscardPendingAssemblies() {
        AutomatedFacility facility = facility();
        facility.setDisplayName("x".repeat(62_000));
        List<AssetStateFramePacket> frames = AssetStateSync.Server.frame(
            AssetSyncPacket.fullSync(facility)
                .withPublishedRevision(0L, 1L));
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        client.receive(frames.get(0));
        client.removeAsset(facility.assetId);
        for (int i = 1; i < frames.size(); i++) client.receive(frames.get(i));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));

        client.clear();
        assertTrue(transport.fullRequests.isEmpty());
    }

    @Test
    void thirdMaximumAssemblyExceedsPendingBudgetAndRequestsRecovery() {
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);
        byte[] firstPayload = new byte[AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES];
        CelestialAsset.ID first = CelestialAsset.ID.create();
        CelestialAsset.ID second = CelestialAsset.ID.create();
        CelestialAsset.ID rejected = CelestialAsset.ID.create();

        client.receive(maximumAssemblyFirstFrame(first, firstPayload));
        client.receive(maximumAssemblyFirstFrame(second, firstPayload));
        client.receive(maximumAssemblyFirstFrame(rejected, firstPayload));

        assertEquals(List.of(rejected), transport.fullRequests);
    }

    @Test
    void reassembledRemovalAndClearOwnTheirClientLifecycleEffects() {
        AutomatedFacility facility = facility();
        AssetStateSync.Client client = new AssetStateSync.Client(new RecordingClientTransport());
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(
            AssetSyncPacket.fullSync(facility)
                .withPublishedRevision(0L, 1L))) {
            client.receive(frame);
        }
        assertTrue(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId) != null);

        for (AssetStateFramePacket frame : AssetStateSync.Server
            .frame(AssetSyncPacket.assetRemoved(facility.assetId))) {
            client.receive(frame);
        }
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));

        AutomatedFacility another = facility();
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(
            AssetSyncPacket.fullSync(another)
                .withPublishedRevision(0L, 1L))) {
            client.receive(frame);
        }
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(AssetSyncPacket.clear())) client.receive(frame);

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(another.assetId));
    }

    @Test
    void frameValidationRejectsInvalidIdsCountsIndicesSizesAndPayloadLengths() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        UUID updateId = UUID.randomUUID();
        byte[] payload = new byte[10];

        assertFalse(new AssetStateFramePacket(assetId, null, 0, 1, 10, payload).isValid());
        assertFalse(new AssetStateFramePacket(assetId, updateId, 0, 0, 10, payload).isValid());
        assertFalse(
            new AssetStateFramePacket(assetId, updateId, 0, AssetStateFramePacket.MAX_FRAME_COUNT + 1, 10, payload)
                .isValid());
        assertFalse(new AssetStateFramePacket(assetId, updateId, 1, 1, 10, payload).isValid());
        assertFalse(new AssetStateFramePacket(assetId, updateId, 0, 1, 0, payload).isValid());
        assertFalse(
            new AssetStateFramePacket(
                assetId,
                updateId,
                0,
                1,
                AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES + 1,
                payload).isValid());
        assertFalse(new AssetStateFramePacket(assetId, updateId, 0, 2, 10, payload).isValid());
        assertFalse(new AssetStateFramePacket(assetId, updateId, 0, 1, 10, new byte[9]).isValid());
        assertThrows(
            IllegalStateException.class,
            () -> new AssetStateFramePacket(assetId, updateId, 0, 1, 10, new byte[9]).toBytes(Unpooled.buffer()));

        ByteBuf oversizedPayload = Unpooled.buffer();
        oversizedPayload.writeBoolean(true);
        PacketUtil.writeId(oversizedPayload, assetId);
        oversizedPayload.writeLong(updateId.getMostSignificantBits());
        oversizedPayload.writeLong(updateId.getLeastSignificantBits());
        oversizedPayload.writeShort(0);
        oversizedPayload.writeShort(2);
        oversizedPayload.writeInt(AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES + 1);
        oversizedPayload.writeShort(AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES + 1);
        oversizedPayload.writeZero(AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES + 1);
        AssetStateFramePacket decoded = new AssetStateFramePacket();
        decoded.fromBytes(oversizedPayload);
        assertFalse(decoded.isValid());
    }

    private static AutomatedFacility facility() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static AssetStateFramePacket maximumAssemblyFirstFrame(CelestialAsset.ID assetId, byte[] payload) {
        return new AssetStateFramePacket(
            assetId,
            UUID.randomUUID(),
            0,
            AssetStateFramePacket.MAX_FRAME_COUNT,
            AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES,
            payload);
    }

    private static List<AssetStateFramePacket> framesForPayload(CelestialAsset.ID assetId, byte[] payload) {
        int payloadLimit = AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES;
        int frameCount = (payload.length + payloadLimit - 1) / payloadLimit;
        UUID updateId = UUID.randomUUID();
        List<AssetStateFramePacket> frames = new ArrayList<>(frameCount);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int from = frameIndex * payloadLimit;
            int to = Math.min(payload.length, from + payloadLimit);
            frames.add(
                new AssetStateFramePacket(
                    assetId,
                    updateId,
                    frameIndex,
                    frameCount,
                    payload.length,
                    Arrays.copyOfRange(payload, from, to)));
        }
        return frames;
    }

    private static AssetStateFramePacket withPayload(AssetStateFramePacket frame, byte[] payload) {
        return new AssetStateFramePacket(
            frame.assetId(),
            frame.updateId(),
            frame.frameIndex(),
            frame.frameCount(),
            frame.declaredTotalSize(),
            payload);
    }

    private static AssetStateFramePacket withFrameIndex(AssetStateFramePacket frame, int frameIndex) {
        return new AssetStateFramePacket(
            frame.assetId(),
            frame.updateId(),
            frameIndex,
            frame.frameCount(),
            frame.declaredTotalSize(),
            frame.payload());
    }

    private static AssetStateFramePacket roundTrip(AssetStateFramePacket frame) {
        ByteBuf buffer = Unpooled.buffer();
        frame.toBytes(buffer);
        AssetStateFramePacket decoded = new AssetStateFramePacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static final class RecordingClientTransport implements AssetStateSync.ClientTransport {

        private final List<CelestialAsset.ID> fullRequests = new ArrayList<>();

        @Override
        public void requestFull(CelestialAsset.ID assetId) {
            fullRequests.add(assetId);
        }
    }
}
