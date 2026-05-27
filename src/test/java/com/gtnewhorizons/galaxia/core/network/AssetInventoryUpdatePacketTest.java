package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class AssetInventoryUpdatePacketTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        LogisticStore.clearSignals();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        LogisticStore.clearSignals();
    }

    @Test
    void applyRejectsPositiveDeltaFromNonCreativeEvenWhenPacketClearsCreativeOnly() throws Exception {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.redstone, 0, null);
        AssetInventoryUpdatePacket packet = roundTripWithCreativeOnlyCleared(
            AssetInventoryUpdatePacket.add(facility.assetId, resource, 64));

        AssetSyncPacket sync = packet.apply(TEAM, false);

        assertNull(sync);
        assertEquals(0L, facility.getItemAmount(resource));
    }

    @Test
    void applyBumpsSyncRevisionForInventoryDelta() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.add(facility.assetId, resource, 64);

        AssetSyncPacket sync = packet.apply(TEAM, true);

        assertEquals(1, facility.getSyncRevision());
        assertEquals(1, sync.syncRevision());
    }

    @Test
    void removePacketRemovesAllMatchingInventory() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        facility.updateItems(resource, 32);
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.remove(facility.assetId, resource);

        AssetSyncPacket sync = packet.apply(TEAM, false);

        assertEquals(0L, facility.getItemAmount(resource));
        assertEquals(1, facility.getSyncRevision());
        assertEquals(1, sync.syncRevision());
    }

    @Test
    void boundPacketSetsInventoryBoundForNonCreativePlayer() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.redstone, 0, null);
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket
            .setBound(facility.assetId, BoundKind.ITEM_LOWER, resource, 48);

        AssetSyncPacket sync = packet.apply(TEAM, false);

        assertEquals(
            48,
            facility.getBound(resource)
                .lowOrDefault());
        assertEquals(1, facility.getSyncRevision());
        assertEquals(1, sync.syncRevision());
    }

    @Test
    void invalidBoundPacketIsRejectedWithoutChangingExistingBounds() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.redstone, 0, null);
        facility.setBound(resource, 320, false);
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket
            .setBound(facility.assetId, BoundKind.ITEM_LOWER, resource, 442);

        AssetSyncPacket sync = packet.apply(TEAM, false);

        assertNull(sync);
        assertEquals(
            0L,
            facility.getBound(resource)
                .lowOrDefault());
        assertEquals(
            320L,
            facility.getBound(resource)
                .upperOrDefault());
        assertEquals(0, facility.getSyncRevision());
    }

    private static AutomatedFacility addFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static AssetInventoryUpdatePacket roundTripWithCreativeOnlyCleared(AssetInventoryUpdatePacket packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        buf.setBoolean(buf.writerIndex() - 1, false);
        AssetInventoryUpdatePacket decoded = new AssetInventoryUpdatePacket();
        decoded.fromBytes(buf);
        return decoded;
    }

}
