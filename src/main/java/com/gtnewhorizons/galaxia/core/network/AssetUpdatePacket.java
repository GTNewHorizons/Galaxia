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

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetUpdatePacket implements IMessage {

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

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, action);
        if (action == Action.RENAME_ASSET) {
            PacketUtil.writeString(buf, displayName == null ? "" : displayName);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        action = PacketUtil.readEnum(buf, Action.class);
        if (action == Action.RENAME_ASSET) {
            displayName = PacketUtil.readString(buf);
        }
    }

    public static class Handler implements IMessageHandler<AssetUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            UUID teamId = GTTeamsCompat.getTeam(player);
            return message.apply(teamId, player);
        }
    }

    public AssetSyncPacket apply(UUID teamId, EntityPlayerMP player) {
        if (teamId == null || assetId == null || action == null) {
            return null;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return null;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return null;
        }

        boolean authorized = switch (action) {
            case REQUEST_FULL_SYNC -> true;
            case DESTROY_ASSET -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.DESTROY_ASSET);
            case START_DECONSTRUCTION -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.DECONSTRUCT_ASSET);
            case CANCEL_CONSTRUCTION -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.BUILD_MODULE);
            case RENAME_ASSET -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.RENAME_ASSET);
        };
        if (!authorized) return null;

        return mutateNoChecks(teamId, asset);
    }

    public AssetSyncPacket mutateNoChecks(UUID teamId, CelestialAsset asset) {
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
                    CelestialAssetStore.registerAsset(teamId, asset);
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
