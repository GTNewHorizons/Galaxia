package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> Server: requests that a new facility module be added to an automated facility.
 *
 * <p>
 * The server creates a {@link ModuleInstance}, validates ownership and per-kind placement rules,
 * then adds it to the target {@link AutomatedFacility}. Station / outpost map placement is carried
 * by the optional {@link StationTileCoord}.
 *
 * <p>
 * Returns an {@link AssetSyncPacket} delta so the requesting client sees the new module in the UI.
 */
public final class AssetBuildModulePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private ModuleInstance.ID moduleId;
    private FacilityModuleKind moduleKind;
    private boolean instantBuild;
    private StationTileCoord tileCoord;

    public AssetBuildModulePacket() {}

    public AssetBuildModulePacket(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleInstance.ID moduleId,
        boolean instantBuild) {
        this(assetId, kind, moduleId, instantBuild, null);
    }

    public AssetBuildModulePacket(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleInstance.ID moduleId,
        boolean instantBuild, StationTileCoord tileCoord) {
        this.assetId = assetId;
        this.moduleKind = kind;
        this.moduleId = moduleId;
        this.instantBuild = instantBuild;
        this.tileCoord = tileCoord;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeId(buf, moduleId);
        PacketUtil.writeEnum(buf, moduleKind);
        buf.writeBoolean(instantBuild);
        boolean hasTile = tileCoord != null;
        buf.writeBoolean(hasTile);
        if (hasTile) PacketUtil.writeTileCoord(buf, tileCoord);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleId = PacketUtil.readModuleId(buf);
        moduleKind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        instantBuild = buf.readBoolean();
        tileCoord = buf.readBoolean() ? PacketUtil.readTileCoord(buf) : null;
    }

    public static final class Handler implements IMessageHandler<AssetBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetBuildModulePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            CelestialAsset asset = CelestialAssetStore.findAsset(packet.assetId);
            if (asset == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: missing asset {} for player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (!(asset instanceof AutomatedFacility state)) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: asset {} is not an automated facility for player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (!CelestialAssetStore.isOwnedBy(TempTeamCompat.getTeam(player), packet.assetId)) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unauthorized access to asset {} by player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            FacilityModuleKind kind = packet.moduleKind;
            if (!kind.isAllowedOn(asset.kind)) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: rejected {} on {} ({}) from player {}",
                    kind,
                    packet.assetId,
                    asset.kind,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            if (packet.tileCoord != null) {
                if (!state.hasStationLayout()) {
                    Galaxia.LOG.warn(
                        "[Outpost] BuildModule: tile placement requested on facility without layout {} from player {}",
                        packet.assetId,
                        player.getGameProfile()
                            .getName());
                    return null;
                }
                StationPlacementValidator.Result placementResult = StationPlacementValidator
                    .validate(state.stationLayout(), packet.tileCoord);
                if (placementResult != StationPlacementValidator.Result.OK) {
                    Galaxia.LOG.warn(
                        "[Outpost] BuildModule: rejected placement at {} on {} ({}) from player {}",
                        packet.tileCoord,
                        packet.assetId,
                        placementResult,
                        player.getGameProfile()
                            .getName());
                    return null;
                }
            }

            ModuleInstance module = kind.createInstance(packet.moduleId);
            if (packet.instantBuild && player.capabilities.isCreativeMode) {
                module.completeConstruction();
            }
            state.addModule(module);

            if (packet.tileCoord != null && state.hasStationLayout()) {
                StationTileState initialState = StationTileState.fromModuleStatus(module.status());
                state.stationLayout()
                    .place(packet.tileCoord, new PlacedTile(module, initialState));
            }

            Galaxia.LOG.debug(
                "[Outpost] BuildModule: queued {} construction on outpost {} (by {})",
                kind.getDisplayName(),
                packet.assetId,
                player.getGameProfile()
                    .getName());

            int moduleIndex = state.modules()
                .size() - 1;
            return AssetSyncPacket.moduleAdded(packet.assetId, moduleIndex, module);
        }
    }
}
