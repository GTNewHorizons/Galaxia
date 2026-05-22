package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
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
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

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
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void applyRejectsPositiveDeltaFromNonCreativeEvenWhenPacketClearsCreativeOnly() throws Exception {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.add(facility.assetId, resource, 64);
        setCreativeOnly(packet, false);

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

    private static AutomatedFacility addFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static void setCreativeOnly(AssetInventoryUpdatePacket packet, boolean value) throws Exception {
        Field field = AssetInventoryUpdatePacket.class.getDeclaredField("creativeOnly");
        field.setAccessible(true);
        field.setBoolean(packet, value);
    }
}
