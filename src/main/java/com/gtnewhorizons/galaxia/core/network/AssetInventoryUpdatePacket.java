package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetInventoryUpdatePacket implements IMessage {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private CelestialAsset.ID assetId;
    private String resourceKey;
    private ItemStackWrapper resource;
    private long delta;
    private boolean creativeOnly;
    private Operation operation = Operation.DELTA;
    private BoundKind boundKind;

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

    public static AssetInventoryUpdatePacket setBound(CelestialAsset.ID assetId, BoundKind kind, String resourceKey,
        long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.operation = Operation.SET_BOUND;
        pkt.boundKind = kind;
        pkt.resourceKey = resourceKey;
        pkt.delta = amount;
        pkt.creativeOnly = false;
        return pkt;
    }

    public static AssetInventoryUpdatePacket setBound(CelestialAsset.ID assetId, BoundKind kind,
        ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket pkt = setBound(assetId, kind, resource.toKey(), amount);
        pkt.resource = resource;
        return pkt;
    }

    public static AssetInventoryUpdatePacket clearBound(CelestialAsset.ID assetId, BoundKind kind, String resourceKey) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.operation = Operation.CLEAR_BOUND;
        pkt.boundKind = kind;
        pkt.resourceKey = resourceKey;
        pkt.delta = 0L;
        pkt.creativeOnly = false;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, operation);
        PacketUtil.writeString(buf, resourceKey);
        buf.writeLong(delta);
        buf.writeBoolean(creativeOnly);
        if (operation != Operation.DELTA) {
            PacketUtil.writeEnum(buf, boundKind);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        operation = PacketUtil.readEnum(buf, Operation.class);
        resourceKey = PacketUtil.readString(buf);
        delta = buf.readLong();
        creativeOnly = buf.readBoolean();
        if (operation != Operation.DELTA) {
            boundKind = PacketUtil.readEnum(buf, BoundKind.class);
        }
    }

    public static class Handler implements IMessageHandler<AssetInventoryUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetInventoryUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            UUID teamId = TempTeamCompat.getTeam(player);
            boolean creative = player.capabilities.isCreativeMode;
            return message.apply(teamId, creative);
        }
    }

    public AssetSyncPacket apply(UUID teamId, boolean creativePlayer) {
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null || !CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            LOG.warn("[Logistics] InventoryDelta: unknown or unauthorized assetId {}", assetId);
            return null;
        }

        if (operation == Operation.SET_BOUND || operation == Operation.CLEAR_BOUND) {
            return applyBoundUpdate(state);
        }

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

        ItemStackWrapper resource = this.resource != null ? this.resource : ItemStackWrapper.fromKey(resourceKey);
        if (resource == null) return null;

        long effectiveDelta = delta;
        if (creativeOnly) {
            effectiveDelta = Math.min(delta, Integer.MAX_VALUE);
        }
        if (effectiveDelta > 0L) {
            effectiveDelta = state.insertInventoryWithoutSync(resource, effectiveDelta);
        } else {
            effectiveDelta = state.addInventoryWithoutSync(resource, effectiveDelta);
        }
        if (effectiveDelta == 0L) return null;
        state.bumpSyncRevision();
        LOG.info("[Logistics] Inventory update: {} x {} on outpost {}", effectiveDelta, resource, assetId);
        return AssetSyncPacket.inventoryUpdate(assetId, resourceKey, effectiveDelta)
            .withSyncRevision(state.getSyncRevision());
    }

    private AssetSyncPacket applyBoundUpdate(AutomatedFacility state) {
        if (boundKind == null || resourceKey == null || resourceKey.isEmpty()) return null;
        if (operation == Operation.SET_BOUND) {
            if (resource != null && boundKind == BoundKind.ITEM_LOWER) {
                state.inventory.setItemLowerBound(resource, delta);
            } else if (resource != null && boundKind == BoundKind.ITEM_UPPER) {
                state.inventory.setItemUpperBound(resource, delta);
            } else {
                state.inventory.setBound(boundKind, resourceKey, delta);
            }
            state.markInventoryBoundDelta(boundKind, resourceKey, true, delta);
            LOG.info("[Logistics] Inventory bound set: {} {}={} on outpost {}", boundKind, resourceKey, delta, assetId);
            return AssetSyncPacket.inventoryBoundUpdate(assetId, boundKind, resourceKey, true, delta)
                .withSyncRevision(state.getSyncRevision());
        }
        if (resource != null && boundKind == BoundKind.ITEM_LOWER) {
            state.inventory.clearItemLowerBound(resource);
        } else if (resource != null && boundKind == BoundKind.ITEM_UPPER) {
            state.inventory.clearItemUpperBound(resource);
        } else {
            state.inventory.clearBound(boundKind, resourceKey);
        }
        state.markInventoryBoundDelta(boundKind, resourceKey, false, 0L);
        LOG.info("[Logistics] Inventory bound cleared: {} {} on outpost {}", boundKind, resourceKey, assetId);
        return AssetSyncPacket.inventoryBoundUpdate(assetId, boundKind, resourceKey, false, 0L)
            .withSyncRevision(state.getSyncRevision());
    }

    private enum Operation {
        DELTA,
        SET_BOUND,
        CLEAR_BOUND
    }
}
