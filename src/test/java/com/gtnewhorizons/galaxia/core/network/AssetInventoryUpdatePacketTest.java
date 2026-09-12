package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
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
    @AfterEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void applyMutatesPhysicalStationInventory() {
        MutableInventory inventory = new MutableInventory();
        Station station = addStation(inventory);
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);

        assertTrue(
            AssetInventoryUpdatePacket.add(station.assetId, resource, 64)
                .apply(TEAM, true));
        assertEquals(64L, inventory.getItemAmount(resource));

        assertTrue(
            AssetInventoryUpdatePacket.removeAmount(station.assetId, resource, 16)
                .apply(TEAM, false));
        assertEquals(48L, inventory.getItemAmount(resource));

        assertTrue(
            AssetInventoryUpdatePacket.remove(station.assetId, resource)
                .apply(TEAM, false));
        assertEquals(0L, inventory.getItemAmount(resource));
    }

    @Test
    void applyRejectsPositiveDeltaFromNonCreativeEvenWhenWireFlagIsCleared() {
        MutableInventory inventory = new MutableInventory();
        Station station = addStation(inventory);
        ItemStackWrapper resource = new ItemStackWrapper(Items.redstone, 0, null);
        AssetInventoryUpdatePacket packet = roundTripWithCreativeOnlyCleared(
            AssetInventoryUpdatePacket.add(station.assetId, resource, 64));

        assertFalse(packet.apply(TEAM, false));
        assertEquals(0L, inventory.getItemAmount(resource));
    }

    private static Station addStation(MutableInventory inventory) {
        Station station = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            Buildable.Status.OPERATIONAL) {

            @Override
            public List<IDistributedInventory> getChildren() {
                return List.of(inventory);
            }
        };
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, station);
        return station;
    }

    private static AssetInventoryUpdatePacket roundTripWithCreativeOnlyCleared(AssetInventoryUpdatePacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        buffer.setBoolean(buffer.writerIndex() - 1, false);
        AssetInventoryUpdatePacket decoded = new AssetInventoryUpdatePacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static final class MutableInventory implements IDistributedInventory {

        private final Map<ItemStackWrapper, Long> items = new LinkedHashMap<>();

        @Override
        public Map<ItemStackWrapper, Long> getItemAmounts() {
            return items;
        }

        @Override
        public long totalItemCapacity() {
            return 1_000L;
        }
    }
}
