package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetInventoryUpdatePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private String resourceKey;
    private long delta;
    private boolean creativeOnly;

    public AssetInventoryUpdatePacket() {}

    public static AssetInventoryUpdatePacket add(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resourceKey = resource.toKey();
        pkt.delta = amount;
        pkt.creativeOnly = true;
        return pkt;
    }

    public static AssetInventoryUpdatePacket remove(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resourceKey = resource.toKey();
        pkt.delta = Long.MIN_VALUE;
        pkt.creativeOnly = false;
        return pkt;
    }

    public static AssetInventoryUpdatePacket removeAmount(CelestialAsset.ID assetId, ItemStackWrapper resource,
        long amount) {
        AssetInventoryUpdatePacket pkt = new AssetInventoryUpdatePacket();
        pkt.assetId = assetId;
        pkt.resourceKey = resource.toKey();
        pkt.delta = -amount;
        pkt.creativeOnly = false;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeString(buf, resourceKey);
        buf.writeLong(delta);
        buf.writeBoolean(creativeOnly);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        resourceKey = PacketUtil.readString(buf);
        delta = buf.readLong();
        creativeOnly = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<AssetInventoryUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetInventoryUpdatePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            String playerName = player.getGameProfile()
                .getName();

            // TODO: Figure out if this path will be only used in creative. If not remove this check and maybe make
            // a factory method that checks for creative mode
            if (packet.creativeOnly && !player.capabilities.isCreativeMode) {
                Galaxia.LOG.warn("[Logistics] InventoryDelta rejected: player {} is not in creative mode.", playerName);
                return null;
            }

            if (packet.creativeOnly && packet.delta <= 0) {
                Galaxia.LOG.warn(
                    "[Logistics] InventoryDelta rejected: invalid amount {} from player {}",
                    packet.delta,
                    playerName);
                return null;
            }

            AutomatedFacility state = CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedFacility o ? o
                : null;
            if (state == null) {
                Galaxia.LOG
                    .warn("[Logistics] InventoryDelta: unknown assetId {} from player {}", packet.assetId, playerName);
                return null;
            }

            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (resource == null) return null;

            if (packet.delta == Long.MIN_VALUE) {
                long amount = state.inventory.getAmount(resource);
                if (amount > 0) {
                    state.inventory.add(resource, -amount);
                    Galaxia.LOG.info(
                        "[Logistics] Removed {} x {} from outpost {} (by {})",
                        amount,
                        resource,
                        packet.assetId,
                        playerName);
                    return AssetSyncPacket.inventoryUpdate(packet.assetId, packet.resourceKey, -amount);
                }
            } else {
                long effectiveDelta = packet.delta;
                if (packet.creativeOnly) {
                    effectiveDelta = Math.min(packet.delta, Integer.MAX_VALUE);
                }
                state.inventory.add(resource, effectiveDelta);
                Galaxia.LOG.info(
                    "[Logistics] Inventory update: {} x {} on outpost {} (by {})",
                    effectiveDelta,
                    resource,
                    packet.assetId,
                    playerName);
                return AssetSyncPacket.inventoryUpdate(packet.assetId, packet.resourceKey, effectiveDelta);
            }
            return null;
        }
    }
}
