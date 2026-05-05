package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

import io.netty.buffer.ByteBuf;

public final class AssetUpdatePacket {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    public enum Action {
        DESTROY_ASSET,
        CANCEL_CONSTRUCTION,
        START_DECONSTRUCTION,
        REQUEST_FULL_SYNC,
        RENAME_ASSET
    }

    private CelestialAsset.ID assetId;
    private Action action;
    private String displayName;

    public AssetUpdatePacket() {}

    public static AssetUpdatePacket create(CelestialAsset.ID assetId, Action action) {
        AssetUpdatePacket pkt = new AssetUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = action;
        return pkt;
    }

    public static AssetUpdatePacket rename(CelestialAsset.ID assetId, String displayName) {
        AssetUpdatePacket pkt = new AssetUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.RENAME_ASSET;
        pkt.displayName = displayName;
        return pkt;
    }

    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, action);
        if (action == Action.RENAME_ASSET) {
            PacketUtil.writeString(buf, displayName == null ? "" : displayName);
        }
    }

    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        action = PacketUtil.readEnum(buf, Action.class);
        if (action == Action.RENAME_ASSET) {
            displayName = PacketUtil.readString(buf);
        }
    }

    public AssetSyncPacket apply(UUID teamId) {
        if (teamId == null || assetId == null || action == null) {
            return null;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return null;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return null;
        }

        return switch (action) {
            case DESTROY_ASSET -> {
                boolean destroyed = CelestialAssetStore.destroyAsset(assetId);
                yield destroyed ? AssetSyncPacket.assetRemoved(assetId) : null;
            }
            case CANCEL_CONSTRUCTION -> {
                boolean cancelled = CelestialAssetStore.cancelConstruction(assetId);
                yield cancelled ? AssetSyncPacket.assetRemoved(assetId) : null;
            }
            case START_DECONSTRUCTION -> {
                boolean started = CelestialAssetStore.startDeconstruction(assetId);
                yield started ? AssetSyncPacket.fullSync(asset) : null;
            }
            case REQUEST_FULL_SYNC -> {
                AutomatedFacility state = asset instanceof AutomatedFacility o ? o : null;
                if (state == null && asset.status() == CelestialAsset.Status.OPERATIONAL) {
                    CelestialAssetStore.add(teamId, asset);
                    LOG.info("[Outpost] Auto-created state for outpost {} (team {})", assetId, teamId);
                    state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
                }
                yield state != null ? AssetSyncPacket.fullSync(state) : null;
            }
            case RENAME_ASSET -> {
                boolean renamed = CelestialAssetStore.renameAsset(assetId, displayName);
                yield renamed ? AssetSyncPacket.fullSync(asset) : null;
            }
        };
    }
}
