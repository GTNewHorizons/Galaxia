package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class AssetStateSyncTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final UUID OTHER_TEAM = UUID.randomUUID();
    private static final UUID FIRST_RECIPIENT = UUID.randomUUID();
    private static final UUID SECOND_RECIPIENT = UUID.randomUUID();
    private static final UUID OTHER_RECIPIENT = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @BeforeEach
    void clearServerStore() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void interactivePublicationCapturesOneStateForEveryEligibleRecipient() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setEnergyStored(100L);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport transport = new RecordingTransport(facility);
        transport.mutateAfterFirstDelivery = true;
        AssetStateSync.Server sync = new AssetStateSync.Server(transport);

        sync.publishInteractive(facility.assetId);

        assertEquals(List.of(FIRST_RECIPIENT, SECOND_RECIPIENT), transport.recipientIds());
        assertEquals(2, transport.payloads.size());
        assertArrayEquals(transport.payloads.get(0), transport.payloads.get(1));
        assertEquals(List.of(1L, 1L), transport.publishedRevisions);
        assertEquals(List.of(AssetSyncPacket.STATE, AssetSyncPacket.STATE), transport.syncTypes);

        transport.clearDeliveries();
        facility.setEnergyStored(300L);
        sync.publishInteractive(facility.assetId);

        assertEquals(List.of(2L, 2L), transport.publishedRevisions);
        assertEquals(List.of(AssetSyncPacket.STATE, AssetSyncPacket.STATE), transport.syncTypes);
    }

    @Test
    void resetRecipientLosesOnlyThatRecipientsPublicationBaseline() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport transport = new RecordingTransport(facility);
        AssetStateSync.Server sync = new AssetStateSync.Server(transport);
        sync.publishInteractive(facility.assetId);
        transport.clearDeliveries();

        sync.resetRecipient(FIRST_RECIPIENT);
        transport.clearDeliveries();
        sync.publishInteractive(facility.assetId);

        assertEquals(List.of(FIRST_RECIPIENT), transport.recipientIds());
        assertEquals(List.of(1L), transport.publishedRevisions);
        assertEquals(List.of(AssetSyncPacket.STATE), transport.syncTypes);
    }

    @Test
    void periodicPublicationRestoresUnchangedAssetAfterRecipientReset() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setEnergyStored(200L);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport transport = new RecordingTransport(facility);
        AssetStateSync.Server sync = new AssetStateSync.Server(transport);
        sync.publishInteractive(facility.assetId);
        transport.clearDeliveries();

        sync.resetRecipient(FIRST_RECIPIENT);
        transport.clearDeliveries();
        sync.publishPeriodic();

        assertEquals(List.of(FIRST_RECIPIENT), transport.recipientIds());
        assertEquals(List.of(1L), transport.publishedRevisions);
        assertEquals(List.of(AssetSyncPacket.STATE), transport.syncTypes);
    }

    @Test
    void destroyAssetRemovesServerStateAndPublishesRemovalOnce() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport transport = new RecordingTransport(facility);
        AssetStateSync.Server sync = new AssetStateSync.Server(transport);
        sync.publishInteractive(facility.assetId);
        transport.clearDeliveries();

        assertTrue(sync.destroyAsset(facility.assetId));

        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
        assertEquals(List.of(FIRST_RECIPIENT, SECOND_RECIPIENT), transport.recipientIds());
        assertEquals(List.of(AssetSyncPacket.ASSET_REMOVED, AssetSyncPacket.ASSET_REMOVED), transport.syncTypes);
    }

    @Test
    void explicitRecoveryReturnsMissedRemovalOnlyToAnEligibleRecipient() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport serverTransport = new RecordingTransport(facility);
        AssetStateSync.Server server = new AssetStateSync.Server(serverTransport);
        server.publishInteractive(facility.assetId);

        AssetStateSync.Client client = new AssetStateSync.Client(new RecordingClientTransport());
        receive(client, roundTrip(serverTransport.packets.get(0)));
        assertTrue(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId) != null);

        serverTransport.clearDeliveries();
        assertTrue(server.destroyAsset(facility.assetId));
        serverTransport.clearDeliveries();

        server.publishFullTo(OTHER_RECIPIENT, facility.assetId);
        assertTrue(serverTransport.packets.isEmpty());

        server.publishFullTo(FIRST_RECIPIENT, facility.assetId);
        assertEquals(List.of(AssetSyncPacket.ASSET_REMOVED), serverTransport.syncTypes);
        receive(client, roundTrip(serverTransport.packets.get(0)));

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
    }

    @Test
    void removalTombstoneRejectsDelayedOlderStateUntilStrictlyNewerStateArrives() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        RecordingClientTransport transport = new RecordingClientTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        receive(
            client,
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(3L));
        receive(client, AssetSyncPacket.assetRemoved(facility.assetId, 5L));
        facility.setEnergyStored(40L);
        receive(
            client,
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(4L));

        assertNull(CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertTrue(transport.fullRequests.isEmpty());

        facility.setEnergyStored(60L);
        receive(
            client,
            AssetSyncPacket.state(TEAM, facility)
                .withPublishedRevision(6L));
        AutomatedFacility restored = (AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId);
        assertEquals(60L, restored.getEnergyStored());
    }

    @Test
    void newerAbsolutePublicationAppliesWithoutPredecessor() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setEnergyStored(100L);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport serverTransport = new RecordingTransport(facility);
        AssetStateSync.Server serverSync = new AssetStateSync.Server(serverTransport);

        serverSync.publishInteractive(facility.assetId);
        AssetSyncPacket full = roundTrip(serverTransport.packets.get(0));
        serverTransport.clearDeliveries();
        facility.setEnergyStored(200L);
        serverSync.publishInteractive(facility.assetId);
        serverTransport.clearDeliveries();
        facility.setEnergyStored(300L);
        serverSync.publishInteractive(facility.assetId);
        AssetSyncPacket gap = roundTrip(serverTransport.packets.get(0));

        RecordingClientTransport clientTransport = new RecordingClientTransport();
        AssetStateSync.Client clientSync = new AssetStateSync.Client(clientTransport);
        receive(clientSync, full);
        AutomatedFacility client = (AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId);
        assertEquals(100L, client.getEnergyStored());

        receive(clientSync, gap);
        receive(clientSync, gap);

        assertEquals(300L, client.getEnergyStored());
        assertTrue(clientTransport.fullRequests.isEmpty());
    }

    @Test
    void absoluteReplacementIsIdempotentAndPreservesClientAssetIdentity() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setEnergyStored(100L);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport serverTransport = new RecordingTransport(facility);
        AssetStateSync.Server serverSync = new AssetStateSync.Server(serverTransport);
        serverSync.publishInteractive(facility.assetId);
        AssetSyncPacket full = roundTrip(serverTransport.packets.get(0));
        serverTransport.clearDeliveries();
        facility.setEnergyStored(250L);
        serverSync.publishInteractive(facility.assetId);
        AssetSyncPacket replacement = roundTrip(serverTransport.packets.get(0));

        RecordingClientTransport clientTransport = new RecordingClientTransport();
        AssetStateSync.Client clientSync = new AssetStateSync.Client(clientTransport);
        receive(clientSync, full);
        AutomatedFacility client = (AutomatedFacility) CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId);

        receive(clientSync, replacement);
        receive(clientSync, replacement);

        assertSame(client, CelestialAssetStore.CLIENT.findAssetInternal(facility.assetId));
        assertEquals(250L, client.getEnergyStored());
        assertTrue(clientTransport.fullRequests.isEmpty());
    }

    @Test
    void canonicalReplacementPreservesAssetIdentityAndClearsAbsentState() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        AutomatedFacility current = new AutomatedFacility(
            assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        current.setEnergyStored(100L);
        current.setFilters(List.of("ore:old"), true);

        AutomatedFacility authoritative = new AutomatedFacility(
            assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.DISABLED);
        authoritative.setEnergyStored(250L);
        authoritative.setDisplayName("canonical");

        AssetState.Decoded decoded = AssetState.decode(AssetState.encode(TEAM, authoritative));
        AssetState.replace(TEAM, current, decoded);

        assertEquals(250L, current.getEnergyStored());
        assertEquals("canonical", current.displayName());
        assertEquals(Buildable.Status.DISABLED, current.status());
        assertFalse(
            current.filtersSnapshot()
                .getOrDefault(true, List.of())
                .contains("ore:old"));
        assertTrue(
            current.modules()
                .isEmpty());
    }

    @Test
    void periodicIntegrityAuditPublishesAProjectionChangeWithoutDirtyState() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        RecordingTransport transport = new RecordingTransport(facility);
        AssetStateSync.Server sync = new AssetStateSync.Server(transport);
        sync.publishPeriodic();
        transport.clearDeliveries();

        facility.setDisplayName("integrity-audit-change");
        sync.publishPeriodic();

        assertEquals(List.of(2L, 2L), transport.publishedRevisions);
        assertEquals(2, transport.payloads.size());
    }

    private static AssetSyncPacket roundTrip(AssetSyncPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        AssetSyncPacket decoded = new AssetSyncPacket();
        decoded.fromBytes(buffer, packet.assetId());
        return decoded;
    }

    private static void receive(AssetStateSync.Client client, AssetSyncPacket packet) {
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(packet)) client.receive(frame);
    }

    private static final class RecordingTransport implements AssetStateSync.ServerTransport {

        private final AutomatedFacility facility;
        private final List<UUID> recipientIds = new ArrayList<>();
        private final List<byte[]> payloads = new ArrayList<>();
        private final List<Long> publishedRevisions = new ArrayList<>();
        private final List<Byte> syncTypes = new ArrayList<>();
        private final List<AssetSyncPacket> packets = new ArrayList<>();
        private boolean mutateAfterFirstDelivery;

        private RecordingTransport(AutomatedFacility facility) {
            this.facility = facility;
        }

        @Override
        public Collection<UUID> eligibleRecipients(UUID teamId) {
            if (TEAM.equals(teamId)) return List.of(FIRST_RECIPIENT, SECOND_RECIPIENT);
            if (OTHER_TEAM.equals(teamId)) return List.of(OTHER_RECIPIENT);
            return List.of();
        }

        @Override
        public void send(UUID recipientId, AssetStateFramePacket frame) {
            assertEquals(1, frame.frameCount());
            ByteBuf framed = Unpooled.wrappedBuffer(frame.payload());
            AssetSyncPacket packet = new AssetSyncPacket();
            packet.fromBytes(framed, frame.assetId());
            ByteBuf buffer = Unpooled.buffer();
            packet.toBytes(buffer);
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), payload);
            recipientIds.add(recipientId);
            payloads.add(payload);
            publishedRevisions.add(packet.publishedRevision());
            syncTypes.add(packet.syncType());
            packets.add(packet);
            if (mutateAfterFirstDelivery && recipientIds.size() == 1) facility.setEnergyStored(200L);
        }

        private List<UUID> recipientIds() {
            return List.copyOf(recipientIds);
        }

        private void clearDeliveries() {
            recipientIds.clear();
            payloads.clear();
            publishedRevisions.clear();
            syncTypes.clear();
            packets.clear();
        }
    }

    private static final class RecordingClientTransport implements AssetStateSync.ClientTransport {

        private final List<CelestialAsset.ID> fullRequests = new ArrayList<>();

        @Override
        public void requestFull(CelestialAsset.ID assetId) {
            fullRequests.add(assetId);
        }
    }
}
