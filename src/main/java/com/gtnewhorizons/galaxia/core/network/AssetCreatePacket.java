package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetCreatePacket implements IMessage {

    private CelestialObjectId celestialObjectId;
    private String displayName;
    private CelestialAsset.Kind kind;
    private Buildable.Status status;

    public AssetCreatePacket() {}

    public AssetCreatePacket(CelestialObjectId celestialObjectId, String displayName, CelestialAsset.Kind kind,
        Buildable.Status status) {
        this.celestialObjectId = celestialObjectId;
        this.displayName = displayName;
        this.kind = kind;
        this.status = status;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeString(buf, celestialObjectId.toString());
        PacketUtil.writeString(buf, displayName == null ? "" : displayName);
        PacketUtil.writeEnum(buf, kind);
        PacketUtil.writeEnum(buf, status);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        celestialObjectId = CelestialObjectId.fromString(PacketUtil.readString(buf));
        displayName = PacketUtil.readString(buf);
        kind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
        status = PacketUtil.readEnum(buf, Buildable.Status.class);
    }

    static AssetSyncPacket createOnServer(UUID teamId, AssetCreatePacket packet) {
        if (teamId == null || packet == null
            || packet.celestialObjectId == null
            || packet.kind == null
            || packet.status == null) {
            return null;
        }
        if (packet.kind == CelestialAsset.Kind.STATION) {
            return null;
        }

        CelestialAsset asset = packet.status == Buildable.Status.OPERATIONAL
            ? CelestialAssetStore
                .createOperationalAsset(teamId, packet.celestialObjectId, packet.displayName, packet.kind)
            : CelestialAssetStore
                .createAssetInConstruction(teamId, packet.celestialObjectId, packet.displayName, packet.kind);

        if (!(asset instanceof AutomatedFacility facility)) return null;
        return AssetSyncPacket.fullSync(facility);
    }

    public static final class Handler implements IMessageHandler<AssetCreatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetCreatePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            return createOnServer(TempTeamCompat.getTeam(player), packet);
        }
    }
}
