package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
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
        return isCompatibleTarget(facility, kind, shape, tier, coord, pendingTargets, 0);
    }

    static boolean isCompatibleTarget(AutomatedFacility facility, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, StationTileCoord coord, Collection<StationTileCoord> pendingTargets, int rotation) {
        return isCompatibleTarget(facility, kind, shape, tier, coord, pendingTargets, null, rotation);
    }

    static boolean isCompatibleTarget(AutomatedFacility facility, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, StationTileCoord coord, Collection<StationTileCoord> pendingTargets,
        Map<StationTileCoord, Integer> pendingRotations, int rotation) {
        if (facility == null || kind == null || shape == null || tier == null || coord == null) return false;
        if (!kind.isAllowedOn(facility.kind) || !kind.allowedTiers()
            .contains(tier)) {
            return false;
        }
        if (!facility.hasStationLayout() || facility.stationLayout() == null) return false;
        if (!hasRequiredAnchorFeature(facility, kind, coord)) return false;
        if (shape == ModuleShape.SINGLE) {
            return isCompatibleSingleTarget(facility, coord, pendingTargets);
        }
        return isCompatibleFootprintTarget(facility, coord, shape, pendingTargets, pendingRotations, rotation);
    }

    static List<StationTileCoord> connectedTargets(AutomatedFacility facility, Collection<StationTileCoord> targets,
        ModuleShape shape) {
        return connectedTargets(facility, targets, shape, 0);
    }

    static List<StationTileCoord> connectedTargets(AutomatedFacility facility, Collection<StationTileCoord> targets,
        ModuleShape shape, int rotation) {
        return connectedTargets(facility, targets, shape, null, rotation);
    }

    static List<StationTileCoord> connectedTargets(AutomatedFacility facility, Collection<StationTileCoord> targets,
        ModuleShape shape, Map<StationTileCoord, Integer> rotations, int rotation) {
        if (facility == null || targets == null
            || targets.isEmpty()
            || shape == null
            || !facility.hasStationLayout()
            || facility.stationLayout() == null) {
            return List.of();
        }
        Set<StationTileCoord> selected = new HashSet<>(targets);
        Set<StationTileCoord> connected = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (StationTileCoord target : selected) {
                if (target == null || connected.contains(target)) continue;
                int targetRotation = rotationFor(rotations, target, rotation);
                if (hasBuiltOrthogonalNeighbour(facility, target, shape, targetRotation)
                    || hasPendingOrthogonalNeighbour(connected, target, shape, rotations, rotation, targetRotation)) {
                    connected.add(target);
                    changed = true;
                }
            }
        } while (changed);

        List<StationTileCoord> result = new ArrayList<>();
        for (StationTileCoord target : targets) {
            if (connected.contains(target)) result.add(target);
        }
        return List.copyOf(result);
    }

    static List<StationTileCoord> connectedTargets(AutomatedFacility facility, Collection<StationTileCoord> targets) {
        return connectedTargets(facility, targets, ModuleShape.SINGLE);
    }

    static StationTileCoord anchorForRotation(StationTileCoord tile, ModuleShape shape, int rotation) {
        return tile;
    }

    static StationTileCoord tileForAnchorRotation(StationTileCoord anchor, ModuleShape shape, int rotation) {
        return anchor;
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

    private static boolean hasRequiredAnchorFeature(AutomatedFacility facility, FacilityModuleKind kind,
        StationTileCoord coord) {
        PlanetaryFeatureKey requiredFeature = kind.requiredAnchorFeature();
        return requiredFeature == null || facility.planetaryFeaturesAt(coord)
            .contains(requiredFeature);
    }

    private static boolean isCompatibleFootprintTarget(AutomatedFacility facility, StationTileCoord coord,
        ModuleShape shape, Collection<StationTileCoord> pendingTargets, Map<StationTileCoord, Integer> pendingRotations,
        int rotation) {
        if (!shape.fitsAt(coord, rotation)) return false;
        StationTileCoord[] footprint = shape.tiles(coord, rotation);
        for (StationTileCoord tile : footprint) {
            if (facility.stationLayout()
                .isOccupied(tile)) return false;
            if (pendingTargets != null
                && overlapsPendingFootprint(pendingTargets, shape, tile, pendingRotations, rotation)) return false;
        }
        if (ModuleFootprint.validate(facility.stationLayout(), coord, shape, rotation) == ShapeValidation.OK)
            return true;
        if (pendingTargets == null) return false;
        return hasPendingOrthogonalNeighbour(pendingTargets, coord, shape, pendingRotations, rotation, rotation);
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets,
        StationTileCoord coord) {
        for (StationTileCoord pending : pendingTargets) {
            if (coord.isOrthogonallyAdjacent(pending)) return true;
        }
        return false;
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets,
        StationTileCoord coord, ModuleShape shape, int rotation) {
        return hasPendingOrthogonalNeighbour(pendingTargets, coord, shape, null, rotation, rotation);
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets,
        StationTileCoord coord, ModuleShape shape, Map<StationTileCoord, Integer> pendingRotations,
        int fallbackRotation, int coordRotation) {
        for (StationTileCoord tile : shape.tiles(coord, coordRotation)) {
            if (hasPendingOrthogonalNeighbour(pendingTargets, shape, tile, pendingRotations, fallbackRotation))
                return true;
        }
        return false;
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets, ModuleShape shape,
        StationTileCoord tile, Map<StationTileCoord, Integer> pendingRotations, int fallbackRotation) {
        for (StationTileCoord pending : pendingTargets) {
            int pendingRotation = rotationFor(pendingRotations, pending, fallbackRotation);
            for (StationTileCoord pendingTile : shape.tiles(pending, pendingRotation)) {
                if (tile.isOrthogonallyAdjacent(pendingTile)) return true;
            }
        }
        return false;
    }

    private static boolean overlapsPendingFootprint(Collection<StationTileCoord> pendingTargets, ModuleShape shape,
        StationTileCoord tile, Map<StationTileCoord, Integer> pendingRotations, int fallbackRotation) {
        for (StationTileCoord pending : pendingTargets) {
            int pendingRotation = rotationFor(pendingRotations, pending, fallbackRotation);
            for (StationTileCoord pendingTile : shape.tiles(pending, pendingRotation)) {
                if (tile.equals(pendingTile)) return true;
            }
        }
        return false;
    }

    private static boolean hasBuiltOrthogonalNeighbour(AutomatedFacility facility, StationTileCoord coord,
        ModuleShape shape, int rotation) {
        for (StationTileCoord tile : shape.tiles(coord, rotation)) {
            if (hasBuiltOrthogonalNeighbour(facility, tile)) return true;
        }
        return false;
    }

    private static boolean hasBuiltOrthogonalNeighbour(AutomatedFacility facility, StationTileCoord coord) {
        return isBuilt(facility, coord.dx() - 1, coord.dy()) || isBuilt(facility, coord.dx() + 1, coord.dy())
            || isBuilt(facility, coord.dx(), coord.dy() - 1)
            || isBuilt(facility, coord.dx(), coord.dy() + 1);
    }

    private static boolean isBuilt(AutomatedFacility facility, int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return false;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return false;
        return facility.stationLayout()
            .isOccupied(StationTileCoord.of(dx, dy));
    }

    private static int rotationFor(Map<StationTileCoord, Integer> rotations, StationTileCoord target,
        int fallbackRotation) {
        if (rotations == null || target == null) return ModuleShape.normalizeRotation(fallbackRotation);
        return ModuleShape.normalizeRotation(rotations.getOrDefault(target, fallbackRotation));
    }
}
