package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.starmap.sync.StarmapActionPayload;
import com.gtnewhorizons.galaxia.core.starmap.sync.StarmapActionResult;
import com.gtnewhorizons.galaxia.core.starmap.sync.StarmapServerActions;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetBuildModulePacket implements IMessage {

    CelestialAsset.ID assetId;
    FacilityModuleKind moduleKind;
    ModuleShape shape;
    ModuleTier tier;
    boolean instantBuild;
    StationTileCoord tileCoord;

    public AssetBuildModulePacket() {}

    public AssetBuildModulePacket(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord tileCoord) {
        this.assetId = assetId;
        this.moduleKind = kind;
        this.shape = shape;
        this.tier = tier;
        this.instantBuild = instantBuild;
        this.tileCoord = tileCoord;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, moduleKind);
        PacketUtil.writeEnum(buf, shape);
        PacketUtil.writeEnum(buf, tier);
        buf.writeBoolean(instantBuild);
        boolean hasTile = tileCoord != null;
        buf.writeBoolean(hasTile);
        if (hasTile) PacketUtil.writeStationTileCoord(buf, tileCoord);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleKind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        shape = PacketUtil.readEnum(buf, ModuleShape.class);
        tier = PacketUtil.readEnum(buf, ModuleTier.class);
        instantBuild = buf.readBoolean();
        tileCoord = buf.readBoolean() ? PacketUtil.readStationTileCoord(buf) : null;
    }

    public static final class Handler implements IMessageHandler<AssetBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetBuildModulePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            StarmapActionPayload payload = StarmapActionPayload.buildModule(
                packet.assetId,
                packet.moduleKind,
                packet.shape,
                packet.tier,
                packet.instantBuild && player.capabilities.isCreativeMode,
                packet.tileCoord);
            StarmapActionResult result = StarmapServerActions.apply(TempTeamCompat.getTeam(player), payload);
            return result.applied() ? result.syncPacket() : null;
        }
    }
}
