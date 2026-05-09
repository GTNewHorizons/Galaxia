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

    public AssetSyncPacket apply(UUID teamId, boolean creativePlayer) {
        if (delta > 0 && !creativePlayer) {
            LOG.warn("[Logistics] InventoryDelta rejected: positive delta {} requires creative mode.", delta);
            return null;
        }

        if (creativeOnly && !creativePlayer) {
            LOG.warn("[Logistics] InventoryDelta rejected: player is not in creative mode.");
            return null;
        }

        if (creativeOnly && delta <= 0) {
            LOG.warn("[Logistics] InventoryDelta rejected: invalid amount {}", delta);
            return null;
        }

        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null || !CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            LOG.warn("[Logistics] InventoryDelta: unknown or unauthorized assetId {}", assetId);
            return null;
        }

        ItemStackWrapper resource = this.resource != null ? this.resource : ItemStackWrapper.fromKey(resourceKey);
        if (resource == null) return null;

        if (delta == Long.MIN_VALUE) {
            long amount = state.inventory.getAmount(resource);
            if (amount > 0) {
                state.inventory.add(resource, -amount);
                state.bumpSyncRevision();
                LOG.info("[Logistics] Removed {} x {} from outpost {}", amount, resource, assetId);
                return AssetSyncPacket.inventoryUpdate(assetId, resourceKey, -amount)
                    .withSyncRevision(state.getSyncRevision());
            }
        } else {
            long effectiveDelta = delta;
            if (creativeOnly) {
                effectiveDelta = Math.min(delta, Integer.MAX_VALUE);
            }
            state.inventory.add(resource, effectiveDelta);
            state.bumpSyncRevision();
            LOG.info("[Logistics] Inventory update: {} x {} on outpost {}", effectiveDelta, resource, assetId);
            return AssetSyncPacket.inventoryUpdate(assetId, resourceKey, effectiveDelta)
                .withSyncRevision(state.getSyncRevision());
        }
        return null;
    }
}
