package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

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

import io.netty.buffer.ByteBuf;

public final class AssetBuildModulePacket {

    private CelestialAsset.ID assetId;
    private FacilityModuleKind moduleKind;
    private ModuleShape shape;
    private ModuleTier tier;
    private boolean instantBuild;
    private StationTileCoord tileCoord;

    public AssetBuildModulePacket() {}

    public static AssetBuildModulePacket create(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord tileCoord) {
        AssetBuildModulePacket pkt = new AssetBuildModulePacket();
        pkt.assetId = assetId;
        pkt.moduleKind = kind;
        pkt.shape = shape;
        pkt.tier = tier;
        pkt.instantBuild = instantBuild;
        pkt.tileCoord = tileCoord;
        return pkt;
    }

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

    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleKind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        shape = PacketUtil.readEnum(buf, ModuleShape.class);
        tier = PacketUtil.readEnum(buf, ModuleTier.class);
        instantBuild = buf.readBoolean();
        tileCoord = buf.readBoolean() ? PacketUtil.readStationTileCoord(buf) : null;
    }

    public AssetSyncPacket apply(UUID teamId, boolean creativePlayer) {
        if (teamId == null || assetId == null || moduleKind == null || shape == null || tier == null) {
            return null;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return null;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return null;
        }

        if (!(asset instanceof AutomatedFacility facility)) {
            return null;
        }

        if (!moduleKind.isAllowedOn(asset.kind)) {
            return null;
        }

        if (!moduleKind.allowedTiers()
            .contains(tier)) {
            return null;
        }

        StationTileCoord anchor = tileCoord;
        if (anchor != null) {
            if (!facility.hasStationLayout()) return null;

            if (shape != ModuleShape.SINGLE) {
                ShapeValidation footprintResult = ModuleFootprint.validate(facility.stationLayout(), anchor, shape);
                if (footprintResult != ShapeValidation.OK) return null;
            } else {
                StationPlacementValidator.Result placementResult = StationPlacementValidator
                    .validate(facility.stationLayout(), anchor);
                if (placementResult != StationPlacementValidator.Result.OK) {
                    return null;
                }
            }
        }

        ModuleInstance module = moduleKind.create(anchor != null ? anchor : StationTileCoord.CORE, shape, tier);

        boolean shouldInstantBuild = instantBuild && creativePlayer;
        if (shouldInstantBuild) module.completeConstruction();

        facility.addModule(module);
        facility.layoutCache()
            .applyMutation(MutationKind.PLACE, moduleKind, module);

        if (facility.hasStationLayout() && module.anchorOrNull() != null) {
            StationTileState initialState = StationTileState.fromModuleStatus(module.status());
            for (StationTileCoord coord : module.shape()
                .tiles(module.anchor())) {
                facility.stationLayout()
                    .place(coord, new PlacedTile(module, initialState));
            }
        }

        return AssetSyncPacket.fullSync(facility);
    }
}
