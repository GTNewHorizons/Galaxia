package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Collection;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleFootprint;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.ShapeValidation;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleBuildPickerModel {

    private ModuleBuildPickerModel() {}

    static boolean isCompatibleTarget(AutomatedFacility facility, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, StationTileCoord coord) {
        return isCompatibleTarget(facility, kind, shape, tier, coord, null);
    }

    static boolean isCompatibleTarget(AutomatedFacility facility, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, StationTileCoord coord, Collection<StationTileCoord> pendingTargets) {
        if (facility == null || kind == null || shape == null || tier == null || coord == null) return false;
        if (!kind.isAllowedOn(facility.kind) || !kind.allowedTiers()
            .contains(tier)) {
            return false;
        }
        if (!facility.hasStationLayout() || facility.stationLayout() == null) return false;
        if (shape == ModuleShape.SINGLE) {
            return isCompatibleSingleTarget(facility, coord, pendingTargets);
        }
        return ModuleFootprint.validate(facility.stationLayout(), coord, shape) == ShapeValidation.OK;
    }

    private static boolean isCompatibleSingleTarget(AutomatedFacility facility, StationTileCoord coord,
        Collection<StationTileCoord> pendingTargets) {
        if (facility.stationLayout()
            .isOccupied(coord)) return false;
        if (pendingTargets != null && pendingTargets.contains(coord)) return false;
        if (StationPlacementValidator.validate(facility.stationLayout(), coord) == StationPlacementValidator.Result.OK)
            return true;
        return pendingTargets != null && hasPendingOrthogonalNeighbour(pendingTargets, coord);
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets,
        StationTileCoord coord) {
        for (StationTileCoord pending : pendingTargets) {
            if (coord.isOrthogonallyAdjacent(pending)) return true;
        }
        return false;
    }
}
