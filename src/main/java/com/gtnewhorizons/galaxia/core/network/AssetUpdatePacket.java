package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetUpdatePacket implements IMessage {

    public enum Action {
        DESTROY_ASSET,
        CANCEL_CONSTRUCTION,
        START_DECONSTRUCTION,
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
            ServerTickTaskQueue.schedule(() -> {
                UUID teamId = GTTeamsCompat.getTeam(player);
                if (!message.apply(teamId, player)) return;
                if (message.action == Action.DESTROY_ASSET || message.action == Action.CANCEL_CONSTRUCTION) {
                    return;
                }
                AssetStateSync.SERVER.publishInteractive(message.assetId);
            });
            return null;
        }
    }

    public boolean apply(UUID teamId, EntityPlayerMP player) {
        if (teamId == null || assetId == null || action == null) {
            return false;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return false;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return false;
        }

        boolean authorized = switch (action) {
            case DESTROY_ASSET -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.DESTROY_ASSET);
            case START_DECONSTRUCTION -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.DECONSTRUCT_ASSET);
            case CANCEL_CONSTRUCTION -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.BUILD_MODULE);
            case RENAME_ASSET -> GTTeamsCompat.hasPermission(teamId, player, TeamAction.RENAME_ASSET);
        };
        if (!authorized) return false;

        return mutateNoChecks(teamId, asset);
    }

    public boolean mutateNoChecks(UUID teamId, CelestialAsset asset) {
        return switch (action) {
            case DESTROY_ASSET -> {
                boolean destroyed = AssetStateSync.SERVER.destroyAsset(assetId);
                yield destroyed;
            }
            case CANCEL_CONSTRUCTION -> {
                boolean cancelled = asset.status() == CelestialAsset.Status.CONSTRUCTION_SITE
                    && AssetStateSync.SERVER.destroyAsset(assetId);
                yield cancelled;
            }
            case START_DECONSTRUCTION -> {
                boolean started = CelestialAssetStore.startDeconstruction(assetId);
                yield started;
            }
            case RENAME_ASSET -> {
                boolean renamed = CelestialAssetStore.renameAsset(assetId, displayName);
                yield renamed;
            }
        };
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    boolean removesAsset() {
        return action == Action.DESTROY_ASSET || action == Action.CANCEL_CONSTRUCTION;
    }
}
