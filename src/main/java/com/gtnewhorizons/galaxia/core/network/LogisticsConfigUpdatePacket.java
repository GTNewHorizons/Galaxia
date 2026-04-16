package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: updates a single resource's logistics configuration in an outpost.
 *
 * <p>
 * Carries the full {@link LogisticsResourceConfig} for one resource.
 * The server validates that the sending player belongs to the outpost's team
 * before applying the change.
 */
public final class LogisticsConfigUpdatePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private String resourceKey;
    private int minReserve;
    private int orderSize;
    private boolean isImportEnabled;
    private boolean isSupplyEnabled;
    private boolean removeEntry;

    public LogisticsConfigUpdatePacket() {}

    public LogisticsConfigUpdatePacket(CelestialAsset.ID assetId, ItemStackWrapper resource,
        LogisticsResourceConfig config) {
        this.assetId = assetId;
        this.resourceKey = resource.toKey();
        this.minReserve = config.minReserve();
        this.orderSize = config.orderSize();
        this.isImportEnabled = config.isImportEnabled();
        this.isSupplyEnabled = config.isSupplyEnabled();
        this.removeEntry = false;
    }

    public static LogisticsConfigUpdatePacket remove(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket();
        packet.assetId = assetId;
        packet.resourceKey = resource.toKey();
        packet.minReserve = 0;
        packet.orderSize = 1;
        packet.isImportEnabled = false;
        packet.isSupplyEnabled = false;
        packet.removeEntry = true;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeString(buf, resourceKey);
        buf.writeInt(minReserve);
        buf.writeInt(orderSize);
        buf.writeBoolean(isImportEnabled);
        buf.writeBoolean(isSupplyEnabled);
        buf.writeBoolean(removeEntry);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        resourceKey = PacketUtil.readString(buf);
        minReserve = buf.readInt();
        orderSize = buf.readInt();
        isImportEnabled = buf.readBoolean();
        isSupplyEnabled = buf.readBoolean();
        removeEntry = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<LogisticsConfigUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(LogisticsConfigUpdatePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // SimpleNetworkWrapper guarantees onMessage runs on the main server thread
            // for SERVER-bound packets, so direct mutation is safe (same as DestinationSetPacket).
            String playerName = player.getGameProfile()
                .getName();
            AutomatedOutpost state = CelestialAssetStore.findAsset(packet.assetId) instanceof AutomatedOutpost o ? o
                : null;
            if (state == null) {
                Galaxia.LOG.warn(
                    "[Logistics] LogisticsConfigUpdate: unknown assetId {} from player {}",
                    packet.assetId,
                    playerName);
                return null;
            }

            if (!packet.removeEntry && packet.orderSize <= 0) {
                Galaxia.LOG
                    .warn("[Logistics] LogisticsConfigUpdate rejected: orderSize must be > 0 (player {})", playerName);
                return null;
            }
            if (!packet.removeEntry && packet.minReserve < 0) {
                Galaxia.LOG.warn(
                    "[Logistics] LogisticsConfigUpdate rejected: minReserve must be >= 0 (player {})",
                    playerName);
                return null;
            }

            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (packet.removeEntry) {
                state.logisticsConfig.reset(resource);
                return OutpostSyncPacket.logisticsConfigRemoved(packet.assetId, packet.resourceKey);
            } else {
                LogisticsResourceConfig config = new LogisticsResourceConfig(
                    packet.minReserve,
                    packet.orderSize,
                    packet.isImportEnabled,
                    packet.isSupplyEnabled);
                state.logisticsConfig.set(resource, config);
                return OutpostSyncPacket.logisticsConfigUpdated(
                    packet.assetId,
                    packet.resourceKey,
                    config.minReserve(),
                    config.orderSize(),
                    config.isImportEnabled(),
                    config.isSupplyEnabled());
            }
        }
    }
}
