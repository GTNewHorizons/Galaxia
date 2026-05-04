package com.gtnewhorizons.galaxia.core.starmap.sync;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public record StarmapActionPayload(StarmapAction action, CelestialAsset.ID assetId, CelestialObjectId bodyId,
    String displayName, CelestialAsset.Kind assetKind, Buildable.Status assetStatus, FacilityModuleKind moduleKind,
    ModuleShape moduleShape, ModuleTier moduleTier, boolean instantBuild, StationTileCoord tileCoord) {

    public static StarmapActionPayload createAsset(CelestialObjectId bodyId, String displayName,
        CelestialAsset.Kind kind, Buildable.Status status) {
        if (bodyId == null) throw new IllegalArgumentException("bodyId must not be null");
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        return new StarmapActionPayload(
            StarmapAction.CREATE_ASSET,
            null,
            bodyId,
            displayName,
            kind,
            status,
            null,
            null,
            null,
            false,
            null);
    }

    public static StarmapActionPayload assetOnly(StarmapAction action, CelestialAsset.ID assetId) {
        if (action == null) throw new IllegalArgumentException("action must not be null");
        if (assetId == null) throw new IllegalArgumentException("assetId must not be null");
        return new StarmapActionPayload(action, assetId, null, null, null, null, null, null, null, false, null);
    }

    public static StarmapActionPayload renameAsset(CelestialAsset.ID assetId, String displayName) {
        if (assetId == null) throw new IllegalArgumentException("assetId must not be null");
        if (displayName == null || displayName.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return new StarmapActionPayload(
            StarmapAction.RENAME_ASSET,
            assetId,
            null,
            displayName,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    }

    public static StarmapActionPayload buildModule(CelestialAsset.ID assetId, FacilityModuleKind kind,
        ModuleShape shape, ModuleTier tier, boolean instantBuild, StationTileCoord coord) {
        if (assetId == null) throw new IllegalArgumentException("assetId must not be null");
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (shape == null) throw new IllegalArgumentException("shape must not be null");
        if (tier == null) throw new IllegalArgumentException("tier must not be null");
        return new StarmapActionPayload(
            StarmapAction.BUILD_MODULE,
            assetId,
            null,
            null,
            null,
            null,
            kind,
            shape,
            tier,
            instantBuild,
            coord);
    }
}
