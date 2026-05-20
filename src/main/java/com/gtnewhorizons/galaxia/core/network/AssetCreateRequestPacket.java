package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetCreateRequestPacket implements IMessage {

    private CelestialObjectId celestialObjectId;
    private String displayName;
    private CelestialAsset.Kind kind;
    private boolean operational;

    private BlockPos controller;

    public AssetCreateRequestPacket() {}

    public static AssetCreateRequestPacket createFacility(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind, boolean operational) {
        AssetCreateRequestPacket pkt = new AssetCreateRequestPacket();

        pkt.celestialObjectId = celestialObjectId;
        pkt.displayName = displayName;
        pkt.kind = kind;
        pkt.operational = operational;

        return pkt;
    }

    public static AssetCreateRequestPacket createStation(CelestialObjectId celestialObjectId, String displayName,
        BlockPos controller) {
        AssetCreateRequestPacket pkt = new AssetCreateRequestPacket();

        pkt.celestialObjectId = celestialObjectId;
        pkt.displayName = displayName;
        pkt.kind = CelestialAsset.Kind.STATION;
        pkt.operational = true;
        pkt.controller = controller;

        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeEnum(buf, celestialObjectId);
        PacketUtil.writeString(buf, displayName);
        PacketUtil.writeEnum(buf, kind);
        buf.writeBoolean(operational);
        if (kind == CelestialAsset.Kind.STATION) {
            buf.writeInt(controller.x());
            buf.writeInt(controller.y());
            buf.writeInt(controller.z());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        celestialObjectId = PacketUtil.readEnum(buf, CelestialObjectId.class);
        displayName = PacketUtil.readString(buf);
        kind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
        operational = buf.readBoolean();
        if (kind == CelestialAsset.Kind.STATION) {
            controller = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    public AssetSyncPacket apply(UUID teamId) {
        CelestialAsset asset = CelestialAsset.create(celestialObjectId, kind, operational);
        asset.setDisplayName(displayName);
        if (kind == CelestialAsset.Kind.STATION) {
            Station station = (Station) asset;
            station.setController(controller);
        }

        CelestialAssetStore.registerAsset(teamId, asset);

        Galaxia.LOG.info("[Outpost] Created asset {} ({}) at {}", asset.assetId, kind, celestialObjectId);

        return AssetSyncPacket.fullSync(asset);
    }

    public static final class Handler implements IMessageHandler<AssetCreateRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(AssetCreateRequestPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!GTTeamsCompat.hasPermission(player, TeamAction.CREATE_ASSET)) return null;

            UUID teamId = GTTeamsCompat.getTeam(player);
            return packet.apply(teamId);
        }
    }
}
