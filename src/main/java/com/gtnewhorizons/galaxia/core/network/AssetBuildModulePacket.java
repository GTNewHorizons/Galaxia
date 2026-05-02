package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleFootprint;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.ShapeValidation;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetBuildModulePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private FacilityModuleKind moduleKind;
    private ModuleShape shape;
    private ModuleTier tier;
    private boolean instantBuild;
    private StationTileCoord tileCoord;

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

            if (!kind.allowedTiers()
                .contains(packet.tier)) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: rejected tier {} for {} on {} from player {}",
                    packet.tier,
                    kind,
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            StationTileCoord anchor = packet.tileCoord;
            if (anchor != null) {
                if (!state.hasStationLayout()) {
                    Galaxia.LOG.warn(
                        "[Outpost] BuildModule: tile placement requested on facility without layout {} from player {}",
                        packet.assetId,
                        player.getGameProfile()
                            .getName());
                    return null;
                }
                if (packet.shape != ModuleShape.SINGLE) {
                    // Multi-tile: validate the full footprint as a group; child tiles are part of this placement.
                    ShapeValidation footprintResult = ModuleFootprint
                        .validate(state.stationLayout(), anchor, packet.shape);
                    if (footprintResult != ShapeValidation.OK) {
                        Galaxia.LOG.warn(
                            "[Outpost] BuildModule: rejected multi-tile footprint at {} shape {} on {} ({}) from player {}",
                            anchor,
                            packet.shape,
                            packet.assetId,
                            footprintResult,
                            player.getGameProfile()
                                .getName());
                        return null;
                    }
                } else {
                    StationPlacementValidator.Result placementResult = StationPlacementValidator
                        .validate(state.stationLayout(), anchor);
                    if (placementResult != StationPlacementValidator.Result.OK) {
                        Galaxia.LOG.warn(
                            "[Outpost] BuildModule: rejected placement at {} on {} ({}) from player {}",
                            anchor,
                            packet.assetId,
                            placementResult,
                            player.getGameProfile()
                                .getName());
                        return null;
                    }
                }
            }

            ModuleInstance module = kind
                .create(anchor != null ? anchor : StationTileCoord.CORE, packet.shape, packet.tier);
            if (packet.instantBuild && player.capabilities.isCreativeMode) {
                module.completeConstruction();
            }
            state.addModule(module);
            state.layoutCache()
                .applyMutation(MutationKind.PLACE, kind, module);

            if (state.hasStationLayout() && module.anchor() != null) {
                StationTileState initialState = StationTileState.fromModuleStatus(module.status());
                for (StationTileCoord coord : module.shape()
                    .tiles(module.anchor())) {
                    state.stationLayout()
                        .place(coord, new PlacedTile(module, initialState));
                }
            }

            Galaxia.LOG.debug(
                "[Outpost] BuildModule: queued {} construction on outpost {} (by {})",
                kind.getDisplayName(),
                packet.assetId,
                player.getGameProfile()
                    .getName());

            int moduleIndex = state.modules()
                .size() - 1;
            return AssetSyncPacket.moduleAdded(packet.assetId, moduleIndex, module)
                .withSyncRevision(state.getSyncRevision());
        }
    }
}
