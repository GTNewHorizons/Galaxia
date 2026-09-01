package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.init.Items;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
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
        AssetSyncPacket update = AssetSyncPacket.state(TEAM, facility)
            .withPublishedRevision(1L);
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
    void completedStaleAndAcceptedUpdatesPreserveNewerPartialAssemblies() {
        AutomatedFacility facility = facility();
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        facility.setEnergyStored(5L);
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(5L))) {
            client.receive(frame);
        }

        facility.setEnergyStored(7L);
        facility.setDisplayName("7".repeat(62_000));
        List<AssetStateFramePacket> revision7 = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(7L));
        assertTrue(revision7.size() > 1);
        client.receive(revision7.get(0));

        facility.setEnergyStored(4L);
        facility.setDisplayName("4");
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(4L))) {
            client.receive(frame);
        }
        for (int i = 1; i < revision7.size(); i++) client.receive(revision7.get(i));
        assertEquals(
            7L,
            ((AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId)).getEnergyStored());

        facility.setEnergyStored(9L);
        facility.setDisplayName("9".repeat(62_000));
        List<AssetStateFramePacket> revision9 = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(9L));
        assertTrue(revision9.size() > 1);
        client.receive(revision9.get(0));

        facility.setEnergyStored(8L);
        facility.setDisplayName("8");
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(8L))) {
            client.receive(frame);
        }
        for (int i = 1; i < revision9.size(); i++) client.receive(revision9.get(i));

        AutomatedFacility applied = (AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId);
        assertEquals(9L, applied.getEnergyStored());
        assertTrue(transport.fullRequests.isEmpty());
    }

    @Test
    void identicalDuplicateIsIdempotentButConflictingDuplicateDropsAssemblyAndRequestsOnce() {
        AutomatedFacility facility = facility();
        facility.setDisplayName("x".repeat(62_000));
        List<AssetStateFramePacket> frames = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(1L));
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
            AssetSyncPacket.state(TEAM, malformedFacility)
                .withPublishedRevision(1L))
            .get(0);
        RecordingClientTransport transport = new RecordingClientTransport();
        AtomicLong now = new AtomicLong();
        AssetStateSync.Client client = new AssetStateSync.Client(transport, now::get);

        client.receive(withFrameIndex(valid, valid.frameCount()));
        client.receive(withFrameIndex(valid, valid.frameCount()));

        AutomatedFacility expiredFacility = facility();
        expiredFacility.setDisplayName("x".repeat(62_000));
        List<AssetStateFramePacket> expiring = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, expiredFacility)
                .withPublishedRevision(1L));
        client.receive(expiring.get(0));
        now.addAndGet(AssetStateSync.Client.ASSEMBLY_TIMEOUT_MILLIS + 1L);
        client.tick();

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(malformedFacility.assetId));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(expiredFacility.assetId));
        assertEquals(List.of(malformedFacility.assetId, expiredFacility.assetId), transport.fullRequests);
    }

    @Test
    void logicalRemovalUsesTheFramedAssetIdentity() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, facility);
        ByteBuf logical = Unpooled.buffer();
        logical.writeByte(AssetSyncPacket.ASSET_REMOVED);
        logical.writeLong(1L);
        byte[] payload = new byte[logical.readableBytes()];
        logical.readBytes(payload);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : framesForPayload(facility.assetId, payload)) client.receive(frame);

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertTrue(transport.fullRequests.isEmpty());
    }

    @Test
    void logicalRemovalRejectsTrailingBytes() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, facility);
        ByteBuf logical = Unpooled.buffer();
        logical.writeByte(AssetSyncPacket.ASSET_REMOVED);
        logical.writeLong(1L);
        logical.writeByte(0);
        byte[] payload = new byte[logical.readableBytes()];
        logical.readBytes(payload);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : framesForPayload(facility.assetId, payload)) client.receive(frame);

        assertEquals(facility, CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void emptyLogicalStateRequestsRecoveryWithoutMutation() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        ByteBuf logical = Unpooled.buffer();
        logical.writeByte(AssetSyncPacket.STATE);
        logical.writeLong(1L);
        byte[] payload = new byte[logical.readableBytes()];
        logical.readBytes(payload);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : framesForPayload(assetId, payload)) client.receive(frame);

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(assetId));
        assertEquals(List.of(assetId), transport.fullRequests);
    }

    @Test
    void canonicalStateIdentityMustMatchTheFramedAssetIdentity() {
        AutomatedFacility facility = facility();
        CelestialAsset.ID differentFrameId = CelestialAsset.ID.create();
        AssetStateFramePacket valid = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(1L))
            .get(0);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : framesForPayload(differentFrameId, valid.payload())) client.receive(frame);

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(differentFrameId));
        assertEquals(List.of(differentFrameId), transport.fullRequests);
    }

    @Test
    void malformedCanonicalInventoryCannotPartiallyMutateAnExistingClientFacility() throws IOException {
        AutomatedFacility authoritative = facility();
        ItemStackWrapper incomingItem = new ItemStackWrapper(Items.diamond, 0, null);
        FluidKey incomingFluid = new FluidKey(FluidRegistry.WATER, null);
        authoritative.restoreInventory(Map.of(incomingItem, 4L, incomingFluid, 1_000L));

        AutomatedFacility current = new AutomatedFacility(
            authoritative.assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper existingItem = new ItemStackWrapper(Items.stick, 0, null);
        FluidKey existingFluid = new FluidKey(FluidRegistry.LAVA, null);
        Map<InventoryKey, Long> existingInventory = Map.of(existingItem, 7L, existingFluid, 500L);
        current.restoreInventory(existingInventory);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, current);

        NBTTagCompound canonical = AssetState.encode(TEAM, authoritative);
        NBTTagList inventory = canonical.getCompoundTag("facility")
            .getTagList("inventory", NBT.TAG_COMPOUND);
        NBTTagCompound invalidEntry = (NBTTagCompound) inventory.getCompoundTagAt(0)
            .copy();
        invalidEntry.setLong("amount", -1L);
        inventory.appendTag(invalidEntry);

        ByteBuf logical = Unpooled.buffer();
        logical.writeByte(AssetSyncPacket.STATE);
        logical.writeLong(2L);
        CompressedStreamTools.write(canonical, new ByteBufOutputStream(logical));
        AssetSyncPacket malformed = new AssetSyncPacket();
        malformed.fromBytes(logical, authoritative.assetId);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(malformed)) client.receive(frame);

        assertSame(current, CelestialAssetStore.CLIENT.findAssetInternal(current.assetId));
        assertEquals(Map.of(existingItem, 7L), current.itemSnapshot());
        assertEquals(Map.of(existingFluid, 500L), current.fluidAmounts());
        assertEquals(List.of(current.assetId), transport.fullRequests);
    }

    @Test
    void logicalDecoderRejectsMalformedCanonicalNbtWithoutMutation() {
        AutomatedFacility facility = facility();
        AssetStateFramePacket valid = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(1L))
            .get(0);
        byte[] payload = valid.payload();
        payload[Byte.BYTES + Long.BYTES] = Byte.MAX_VALUE;
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        client.receive(withPayload(valid, payload));

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void logicalDecoderRequiresFullCanonicalNbtConsumption() {
        AutomatedFacility facility = facility();
        AssetStateFramePacket valid = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(1L))
            .get(0);
        byte[] trailing = Arrays.copyOf(valid.payload(), valid.payload().length + 1);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        for (AssetStateFramePacket frame : framesForPayload(facility.assetId, trailing)) client.receive(frame);

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(List.of(facility.assetId), transport.fullRequests);
    }

    @Test
    void removalRejectsOlderPendingStateAndClearDiscardsPendingAssemblies() {
        AutomatedFacility facility = facility();
        facility.setDisplayName("x".repeat(62_000));
        List<AssetStateFramePacket> frames = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(1L));
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        client.receive(frames.get(0));
        for (AssetStateFramePacket removal : AssetStateSync.Server
            .frame(AssetSyncPacket.assetRemoved(facility.assetId, 2L))) {
            client.receive(removal);
        }
        for (int i = 1; i < frames.size(); i++) client.receive(frames.get(i));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));

        AutomatedFacility pending = facility();
        pending.setDisplayName("y".repeat(62_000));
        List<AssetStateFramePacket> pendingFrames = AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, pending)
                .withPublishedRevision(1L));
        client.receive(pendingFrames.get(0));
        for (AssetStateFramePacket clear : AssetStateSync.Server.frame(AssetSyncPacket.clear())) client.receive(clear);
        for (int i = 1; i < pendingFrames.size(); i++) client.receive(pendingFrames.get(i));
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(pending.assetId));
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
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(1L))) {
            client.receive(frame);
        }
        assertTrue(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId) != null);

        for (AssetStateFramePacket frame : AssetStateSync.Server
            .frame(AssetSyncPacket.assetRemoved(facility.assetId, 2L))) {
            client.receive(frame);
        }
        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));

        AutomatedFacility another = facility();
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(
            AssetSyncPacket.state(TEAM, another)
                .withPublishedRevision(1L))) {
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

        AssetStateFramePacket maximumBody = maximumAssemblyFirstFrame(
            assetId,
            new byte[AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES]);
        ByteBuf encodedMaximum = Unpooled.buffer();
        maximumBody.toBytes(encodedMaximum);
        assertEquals(AssetStateFramePacket.MAX_MESSAGE_BODY_BYTES, encodedMaximum.readableBytes());
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
