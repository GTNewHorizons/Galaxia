package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

import io.netty.buffer.ByteBuf;

/**
 * Client → Server: updates a single resource's logistics configuration in an outpost.
 *
 * <p>
 * Carries the full {@link LogisticsResourceConfig} for one resource.
 * The server validates that the sending player belongs to the outpost's team
 * before applying the change.
 */
public final class LogisticsConfigUpdatePacket {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private CelestialAsset.ID assetId;
    private String resourceKey;
    private ItemStackWrapper resource;
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
        this.resource = resource;
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
        packet.resource = resource;
        packet.minReserve = 0;
        packet.orderSize = 1;
        packet.isImportEnabled = false;
        packet.isSupplyEnabled = false;
        packet.removeEntry = true;
        return packet;
    }

    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeString(buf, resourceKey);
        buf.writeInt(minReserve);
        buf.writeInt(orderSize);
        buf.writeBoolean(isImportEnabled);
        buf.writeBoolean(isSupplyEnabled);
        buf.writeBoolean(removeEntry);
    }

    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        resourceKey = PacketUtil.readString(buf);
        minReserve = buf.readInt();
        orderSize = buf.readInt();
        isImportEnabled = buf.readBoolean();
        isSupplyEnabled = buf.readBoolean();
        removeEntry = buf.readBoolean();
    }

    public static AssetSyncPacket apply(UUID teamId, LogisticsConfigUpdatePacket packet) {
        AutomatedFacility state = CelestialAssetStore.findAsset(packet.assetId) instanceof AutomatedFacility o ? o
            : null;
        if (state == null || !CelestialAssetStore.isOwnedBy(teamId, packet.assetId)) {
            LOG.warn("[Logistics] LogisticsConfigUpdate: unknown or unauthorized assetId {}", packet.assetId);
            return null;
        }

        if (!packet.removeEntry && packet.orderSize <= 0) {
            LOG.warn("[Logistics] LogisticsConfigUpdate rejected: orderSize must be > 0");
            return null;
        }
        if (!packet.removeEntry && packet.minReserve < 0) {
            LOG.warn("[Logistics] LogisticsConfigUpdate rejected: minReserve must be >= 0");
            return null;
        }

        ItemStackWrapper resource = packet.resource != null ? packet.resource
            : ItemStackWrapper.fromKey(packet.resourceKey);
        if (resource == null) return null;
        if (packet.removeEntry) {
            state.logisticsConfig.reset(resource);
            return AssetSyncPacket.logisticsConfigRemoved(packet.assetId, packet.resourceKey);
        } else {
            LogisticsResourceConfig config = new LogisticsResourceConfig(
                packet.minReserve,
                packet.orderSize,
                packet.isImportEnabled,
                packet.isSupplyEnabled);
            state.logisticsConfig.set(resource, config);
            return AssetSyncPacket.logisticsConfigUpdated(
                packet.assetId,
                packet.resourceKey,
                config.minReserve(),
                config.orderSize(),
                config.isImportEnabled(),
                config.isSupplyEnabled());
        }
    }
}
