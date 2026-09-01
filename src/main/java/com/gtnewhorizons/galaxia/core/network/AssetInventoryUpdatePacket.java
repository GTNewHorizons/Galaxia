package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
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

    public AssetInventoryUpdatePacket() {}

    public static AssetInventoryUpdatePacket add(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket packet = new AssetInventoryUpdatePacket();
        packet.assetId = assetId;
        packet.resource = resource;
        packet.delta = amount;
        packet.creativeOnly = true;
        return packet;
    }

    public static AssetInventoryUpdatePacket remove(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        AssetInventoryUpdatePacket packet = new AssetInventoryUpdatePacket();
        packet.assetId = assetId;
        packet.resource = resource;
        packet.delta = Long.MIN_VALUE;
        return packet;
    }

    public static AssetInventoryUpdatePacket removeAmount(CelestialAsset.ID assetId, ItemStackWrapper resource,
        long amount) {
        AssetInventoryUpdatePacket packet = new AssetInventoryUpdatePacket();
        packet.assetId = assetId;
        packet.resource = resource;
        packet.delta = -amount;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeInventoryKey(buf, resource);
        buf.writeLong(delta);
        buf.writeBoolean(creativeOnly);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        resource = PacketUtil.readInventoryKey(buf);
        delta = buf.readLong();
        creativeOnly = buf.readBoolean();
    }

    public boolean apply(UUID teamId, boolean creativePlayer) {
        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null || !CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            LOG.warn("[Logistics] InventoryDelta: unknown or unauthorized assetId {}", assetId);
            return false;
        }
        if (asset instanceof AutomatedFacility || !(asset instanceof IDistributedInventory physicalInventory)
            || resource == null) {
            return false;
        }
        if (delta > 0 && !creativePlayer) {
            LOG.warn("[Logistics] InventoryDelta rejected: positive delta {} requires creative mode.", delta);
            return false;
        }
        if (creativeOnly && (!creativePlayer || delta <= 0)) return false;

        long applied = physicalInventory.updateContents(resource, delta);
        if (applied == 0L) return false;
        asset.bumpStateRevision();
        long signedApplied = delta > 0L ? applied : -applied;
        LOG.info("[Logistics] Inventory update: {} x {} on {}", signedApplied, resource.toKey(), assetId);
        return true;
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    public static class Handler implements IMessageHandler<AssetInventoryUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetInventoryUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ServerTickTaskQueue.schedule(() -> {
                if (!GTTeamsCompat.hasPermission(player, TeamAction.MANAGE_INVENTORY)) return;
                UUID teamId = GTTeamsCompat.getTeam(player);
                if (message.apply(teamId, player.capabilities.isCreativeMode)) {
                    AssetStateSync.SERVER.publishInteractive(message.assetId);
                }
            });
            return null;
        }
    }
}
