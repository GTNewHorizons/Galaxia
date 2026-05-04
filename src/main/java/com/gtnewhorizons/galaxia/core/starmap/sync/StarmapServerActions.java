package com.gtnewhorizons.galaxia.core.starmap.sync;

import java.util.UUID;

import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleFootprint;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.ShapeValidation;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

public final class StarmapServerActions {

    private StarmapServerActions() {}

    public static StarmapActionResult apply(UUID teamId, StarmapActionPayload payload) {
        if (teamId == null || payload == null || payload.action() == null) {
            return StarmapActionResult.rejected("invalid_payload");
        }
        return switch (payload.action()) {
            case CREATE_ASSET -> createAsset(teamId, payload);
            case DESTROY_ASSET -> destroyAsset(teamId, payload);
            case RENAME_ASSET -> renameAsset(teamId, payload);
            case CANCEL_CONSTRUCTION -> cancelConstruction(teamId, payload);
            case START_DECONSTRUCTION -> startDeconstruction(teamId, payload);
            case BUILD_MODULE -> buildModule(teamId, payload);
            case REQUEST_FULL_SYNC -> requestFullSync(teamId, payload);
            default -> StarmapActionResult.rejected("unsupported_action");
        };
    }

    private static StarmapActionResult createAsset(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = payload.assetStatus() == Buildable.Status.OPERATIONAL
            ? CelestialAssetStore
                .createOperationalAsset(teamId, payload.bodyId(), payload.displayName(), payload.assetKind())
            : CelestialAssetStore
                .createAssetInConstruction(teamId, payload.bodyId(), payload.displayName(), payload.assetKind());
        return StarmapActionResult.applied(AssetSyncPacket.fullSync(asset));
    }

    private static StarmapActionResult destroyAsset(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = findOwnedAsset(teamId, payload.assetId());
        if (asset == null) return StarmapActionResult.rejected("missing_or_unauthorized");
        return CelestialAssetStore.destroyAsset(payload.assetId())
            ? StarmapActionResult.applied(AssetSyncPacket.assetRemoved(payload.assetId()))
            : StarmapActionResult.rejected("destroy_failed");
    }

    private static StarmapActionResult renameAsset(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = findOwnedAsset(teamId, payload.assetId());
        if (asset == null) return StarmapActionResult.rejected("missing_or_unauthorized");
        if (!CelestialAssetStore.renameAsset(payload.assetId(), payload.displayName())) {
            return StarmapActionResult.rejected("rename_failed");
        }
        return StarmapActionResult.applied(AssetSyncPacket.fullSync(asset));
    }

    private static StarmapActionResult cancelConstruction(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = findOwnedAsset(teamId, payload.assetId());
        if (asset == null) return StarmapActionResult.rejected("missing_or_unauthorized");
        return CelestialAssetStore.cancelConstruction(payload.assetId())
            ? StarmapActionResult.applied(AssetSyncPacket.assetRemoved(payload.assetId()))
            : StarmapActionResult.rejected("cancel_failed");
    }

    private static StarmapActionResult startDeconstruction(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = findOwnedAsset(teamId, payload.assetId());
        if (asset == null) return StarmapActionResult.rejected("missing_or_unauthorized");
        if (!CelestialAssetStore.startDeconstruction(payload.assetId())) {
            return StarmapActionResult.rejected("deconstruction_failed");
        }
        return StarmapActionResult.applied(AssetSyncPacket.fullSync(asset));
    }

    private static StarmapActionResult requestFullSync(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = findOwnedAsset(teamId, payload.assetId());
        if (asset == null) return StarmapActionResult.rejected("missing_or_unauthorized");
        return StarmapActionResult.applied(AssetSyncPacket.fullSync(asset));
    }

    private static StarmapActionResult buildModule(UUID teamId, StarmapActionPayload payload) {
        CelestialAsset asset = CelestialAssetStore.findAsset(payload.assetId());
        if (asset == null) return StarmapActionResult.rejected("missing_asset");
        if (!CelestialAssetStore.isOwnedBy(teamId, payload.assetId())) {
            return StarmapActionResult.rejected("unauthorized");
        }
        if (!(asset instanceof AutomatedFacility facility)) {
            return StarmapActionResult.rejected("not_automated_facility");
        }
        if (!payload.moduleKind()
            .isAllowedOn(asset.kind)) {
            return StarmapActionResult.rejected("module_not_allowed");
        }
        if (!payload.moduleKind()
            .allowedTiers()
            .contains(payload.moduleTier())) {
            return StarmapActionResult.rejected("tier_not_allowed");
        }

        StationTileCoord anchor = payload.tileCoord();
        if (anchor != null) {
            if (!facility.hasStationLayout()) return StarmapActionResult.rejected("layout_missing");
            if (payload.moduleShape() != ModuleShape.SINGLE) {
                ShapeValidation footprintResult = ModuleFootprint
                    .validate(facility.stationLayout(), anchor, payload.moduleShape());
                if (footprintResult != ShapeValidation.OK) return StarmapActionResult.rejected("invalid_footprint");
            } else {
                StationPlacementValidator.Result placementResult = StationPlacementValidator
                    .validate(facility.stationLayout(), anchor);
                if (placementResult != StationPlacementValidator.Result.OK) {
                    return StarmapActionResult.rejected("invalid_placement");
                }
            }
        }

        ModuleInstance module = payload.moduleKind()
            .create(anchor != null ? anchor : StationTileCoord.CORE, payload.moduleShape(), payload.moduleTier());
        if (payload.instantBuild()) module.completeConstruction();
        facility.addModule(module);
        facility.layoutCache()
            .applyMutation(MutationKind.PLACE, payload.moduleKind(), module);

        if (facility.hasStationLayout() && module.anchorOrNull() != null) {
            StationTileState initialState = StationTileState.fromModuleStatus(module.status());
            for (StationTileCoord coord : module.shape()
                .tiles(module.anchor())) {
                facility.stationLayout()
                    .place(coord, new PlacedTile(module, initialState));
            }
        }

        return StarmapActionResult.applied(AssetSyncPacket.fullSync(facility));
    }

    private static CelestialAsset findOwnedAsset(UUID teamId, CelestialAsset.ID assetId) {
        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null || !CelestialAssetStore.isOwnedBy(teamId, assetId)) return null;
        return asset;
    }
}
