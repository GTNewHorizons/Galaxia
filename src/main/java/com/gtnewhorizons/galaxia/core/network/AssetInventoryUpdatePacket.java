package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import io.netty.buffer.ByteBuf;

public final class AssetInventoryUpdatePacket {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private CelestialAsset.ID assetId;
    private String resourceKey;
    private ItemStackWrapper resource;
    private long delta;
    private boolean creativeOnly;

    public AssetInventoryUpdatePacket() {}

    public static AssetInventoryUpdatePacket add(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resourceKey = resource.toKey();
        pkt.resource = resource;
        pkt.delta = amount;
        pkt.creativeOnly = true;
        return pkt;
    }

    public static AssetInventoryUpdatePacket remove(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resourceKey = resource.toKey();
        pkt.resource = resource;
        pkt.delta = Long.MIN_VALUE;
        pkt.creativeOnly = false;
        return pkt;
    }

    public static AssetInventoryUpdatePacket removeAmount(CelestialAsset.ID assetId, ItemStackWrapper resource,
        long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resourceKey = resource.toKey();
        pkt.resource = resource;
        pkt.delta = -amount;
        pkt.creativeOnly = false;
        return pkt;
    }

    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeString(buf, resourceKey);
        buf.writeLong(delta);
        buf.writeBoolean(creativeOnly);
    }

    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        resourceKey = PacketUtil.readString(buf);
        delta = buf.readLong();
        creativeOnly = buf.readBoolean();
    }

    public static AssetSyncPacket apply(UUID teamId, boolean creativePlayer, AssetInventoryUpdatePacket packet) {
        if (packet.creativeOnly && !creativePlayer) {
            LOG.warn("[Logistics] InventoryDelta rejected: player is not in creative mode.");
            return null;
        }

        if (packet.creativeOnly && packet.delta <= 0) {
            LOG.warn("[Logistics] InventoryDelta rejected: invalid amount {}", packet.delta);
            return null;
        }

        AutomatedFacility state = CelestialAssetStore.findAsset(packet.assetId) instanceof AutomatedFacility o ? o
            : null;
        if (state == null || !CelestialAssetStore.isOwnedBy(teamId, packet.assetId)) {
            LOG.warn("[Logistics] InventoryDelta: unknown or unauthorized assetId {}", packet.assetId);
            return null;
        }

        ItemStackWrapper resource = packet.resource != null ? packet.resource
            : ItemStackWrapper.fromKey(packet.resourceKey);
        if (resource == null) return null;

        if (packet.delta == Long.MIN_VALUE) {
            long amount = state.inventory.getAmount(resource);
            if (amount > 0) {
                state.inventory.add(resource, -amount);
                LOG.info("[Logistics] Removed {} x {} from outpost {}", amount, resource, packet.assetId);
                return AssetSyncPacket.inventoryUpdate(packet.assetId, packet.resourceKey, -amount);
            }
        } else {
            long effectiveDelta = packet.delta;
            if (packet.creativeOnly) {
                effectiveDelta = Math.min(packet.delta, Integer.MAX_VALUE);
            }
            state.inventory.add(resource, effectiveDelta);
            LOG.info("[Logistics] Inventory update: {} x {} on outpost {}", effectiveDelta, resource, packet.assetId);
            return AssetSyncPacket.inventoryUpdate(packet.assetId, packet.resourceKey, effectiveDelta);
        }
        return null;
    }
}
