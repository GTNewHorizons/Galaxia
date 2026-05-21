package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
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

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private CelestialAsset.ID assetId;
    private InventoryKey resource;
    private int minReserve;
    private int orderSize;
    private boolean isImportEnabled;
    private boolean isSupplyEnabled;
    private boolean removeEntry;

    public LogisticsConfigUpdatePacket() {}

    public LogisticsConfigUpdatePacket(CelestialAsset.ID assetId, InventoryKey resource,
        LogisticsResourceConfig config) {
        this.assetId = assetId;
        this.resource = resource;
        this.minReserve = config.minReserve();
        this.orderSize = config.orderSize();
        this.isImportEnabled = config.isImportEnabled();
        this.isSupplyEnabled = config.isSupplyEnabled();
        this.removeEntry = false;
    }

    public static LogisticsConfigUpdatePacket remove(CelestialAsset.ID assetId, InventoryKey resource) {
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket();
        packet.assetId = assetId;
        packet.resource = resource;
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
        PacketUtil.writeInventoryKey(buf, resource);
        buf.writeInt(minReserve);
        buf.writeInt(orderSize);
        buf.writeBoolean(isImportEnabled);
        buf.writeBoolean(isSupplyEnabled);
        buf.writeBoolean(removeEntry);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        resource = PacketUtil.readInventoryKey(buf);
        minReserve = buf.readInt();
        orderSize = buf.readInt();
        isImportEnabled = buf.readBoolean();
        isSupplyEnabled = buf.readBoolean();
        removeEntry = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<LogisticsConfigUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(LogisticsConfigUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!GTTeamsCompat.hasPermission(player, TeamAction.CONFIGURE_LOGISTICS)) return null;
            UUID teamId = GTTeamsCompat.getTeam(player);
            return message.apply(teamId);
        }
    }

    public AssetSyncPacket apply(UUID teamId) {
        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null || !CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            LOG.warn("[Logistics] LogisticsConfigUpdate: unknown or unauthorized assetId {}", assetId);
            return null;
        }

        if (!removeEntry && orderSize <= 0) {
            LOG.warn("[Logistics] LogisticsConfigUpdate rejected: orderSize must be >0");
            return null;
        }
        if (!removeEntry && minReserve < 0) {
            LOG.warn("[Logistics] LogisticsConfigUpdate rejected: minReserve must be >=0");
            return null;
        }

        if (resource == null) return null;
        if (removeEntry) {
            asset.logisticsConfig.reset(resource);
            asset.bumpSyncRevision();
            return AssetSyncPacket.logisticsConfigRemoved(assetId, resource)
                .withSyncRevision(asset.getSyncRevision());
        } else {
            LogisticsResourceConfig config = new LogisticsResourceConfig(
                minReserve,
                orderSize,
                isImportEnabled,
                isSupplyEnabled);
            asset.logisticsConfig.set(resource, config);
            asset.bumpSyncRevision();
            return AssetSyncPacket
                .logisticsConfigUpdated(
                    assetId,
                    resource,
                    config.minReserve(),
                    config.orderSize(),
                    config.isImportEnabled(),
                    config.isSupplyEnabled())
                .withSyncRevision(asset.getSyncRevision());
        }
    }
}
