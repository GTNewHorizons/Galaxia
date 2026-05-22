package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetInventoryUpdatePacket implements IMessage {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private CelestialAsset.ID assetId;
    private InventoryKey resource;
    private long delta;
    private boolean creativeOnly;
    private Operation operation = Operation.DELTA;
    private BoundKind boundKind;

    public AssetInventoryUpdatePacket() {}

    public static AssetInventoryUpdatePacket add(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resource = resource;
        pkt.delta = amount;
        pkt.creativeOnly = true;
        return pkt;
    }

    public static AssetInventoryUpdatePacket remove(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resource = resource;
        pkt.delta = Long.MIN_VALUE;
        pkt.creativeOnly = false;
        return pkt;
    }

    public static AssetInventoryUpdatePacket removeAmount(CelestialAsset.ID assetId, ItemStackWrapper resource,
        long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resource = resource;
        pkt.delta = -amount;
        pkt.creativeOnly = false;
        return pkt;
    }

    public static AssetInventoryUpdatePacket setBound(CelestialAsset.ID assetId, BoundKind kind, InventoryKey resource,
        long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.operation = Operation.SET_BOUND;
        pkt.boundKind = kind;
        pkt.resource = resource;
        pkt.delta = amount;
        pkt.creativeOnly = false;
        return pkt;
    }

    public static AssetInventoryUpdatePacket clearBound(CelestialAsset.ID assetId, BoundKind kind,
        InventoryKey resource) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.operation = Operation.CLEAR_BOUND;
        pkt.boundKind = kind;
        pkt.resource = resource;
        pkt.delta = 0L;
        pkt.creativeOnly = false;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, operation);
        PacketUtil.writeInventoryKey(buf, resource);
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
        resource = PacketUtil.readInventoryKey(buf);
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
            if (!GTTeamsCompat.hasPermission(player, TeamAction.MANAGE_INVENTORY)) return null;
            UUID teamId = GTTeamsCompat.getTeam(player);
            boolean creative = player.capabilities.isCreativeMode;
            return message.apply(teamId, creative);
        }
    }

    public AssetSyncPacket apply(UUID teamId, boolean creativePlayer) {
        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null || !CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            LOG.warn("[Logistics] InventoryDelta: unknown or unauthorized assetId {}", assetId);
            return null;
        }

        if (operation == Operation.SET_BOUND || operation == Operation.CLEAR_BOUND) {
            if (asset instanceof AutomatedFacility state) {
                return applyBoundUpdate(state);
            }
            return null;
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

        if (this.resource == null) return null;
        int intDelta;
        if (delta > 0) {
            intDelta = (int) Math.min(delta, Integer.MAX_VALUE);
        } else if (delta == Long.MIN_VALUE) {
            intDelta = Integer.MIN_VALUE + 1;
        } else {
            intDelta = (int) Math.max(delta, Integer.MIN_VALUE + 1);
        }
        final long applied = asset.updateContents(resource, intDelta) * (delta > 0 ? 1 : -1);
        if (applied == 0L) return null;

        asset.bumpSyncRevision();
        LOG.info("[Logistics] Inventory update: {} x {} on {}", applied, resource.toKey(), assetId);
        return AssetSyncPacket.inventoryUpdate(assetId, resource, applied)
            .withSyncRevision(asset.getSyncRevision());
    }

    private AssetSyncPacket applyBoundUpdate(AutomatedFacility state) {
        if (boundKind == null) return null;
        if (operation == Operation.SET_BOUND) {
            final boolean low = boundKind == BoundKind.ITEM_LOWER || boundKind == BoundKind.FLUID_LOWER;
            state.setBound(resource, delta, low);

            state.markInventoryBoundDelta(boundKind, resource, true, delta);
            LOG.info(
                "[Logistics] Inventory bound set: {} {}={} on outpost {}",
                boundKind,
                resource.toKey(),
                delta,
                assetId);
            return AssetSyncPacket.inventoryBoundUpdate(assetId, boundKind, resource, true, delta)
                .withSyncRevision(state.getSyncRevision());
        } else if (operation == Operation.CLEAR_BOUND) {
            state.clearBound(resource);

            state.markInventoryBoundDelta(boundKind, resource, false, 0L);
            LOG.info("[Logistics] Inventory bound cleared: {} {} on outpost {}", boundKind, resource.toKey(), assetId);
            return AssetSyncPacket.inventoryBoundUpdate(assetId, boundKind, resource, false, 0L)
                .withSyncRevision(state.getSyncRevision());

        } else {
            throw new IllegalStateException("[Logistics] Received malformed bound update");
        }
    }

    private enum Operation {
        DELTA,
        SET_BOUND,
        CLEAR_BOUND
    }
}
