package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
        return isCompatibleFootprintTarget(facility, coord, shape, pendingTargets);
    }

    static List<StationTileCoord> connectedTargets(AutomatedFacility facility, Collection<StationTileCoord> targets,
        ModuleShape shape) {
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
                if (hasBuiltOrthogonalNeighbour(facility, target, shape)
                    || hasPendingOrthogonalNeighbour(connected, target, shape)) {
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
        if (tile == null || shape != ModuleShape.QUAD_2x2) return tile;
        int normalizedRotation = Math.floorMod(rotation, 4);
        int anchorDx = tile.dx() - switch (normalizedRotation) {
            case 1, 2 -> 1;
            default -> 0;
        };
        int anchorDy = tile.dy() - switch (normalizedRotation) {
            case 2, 3 -> 1;
            default -> 0;
        };
        if (anchorDx < StationTileCoord.MIN || anchorDx > StationTileCoord.MAX) return null;
        if (anchorDy < StationTileCoord.MIN || anchorDy > StationTileCoord.MAX) return null;
        return StationTileCoord.of(anchorDx, anchorDy);
    }

    static StationTileCoord tileForAnchorRotation(StationTileCoord anchor, ModuleShape shape, int rotation) {
        if (anchor == null || shape != ModuleShape.QUAD_2x2) return anchor;
        int normalizedRotation = Math.floorMod(rotation, 4);
        int tileDx = anchor.dx() + switch (normalizedRotation) {
            case 1, 2 -> 1;
            default -> 0;
        };
        int tileDy = anchor.dy() + switch (normalizedRotation) {
            case 2, 3 -> 1;
            default -> 0;
        };
        if (tileDx < StationTileCoord.MIN || tileDx > StationTileCoord.MAX) return null;
        if (tileDy < StationTileCoord.MIN || tileDy > StationTileCoord.MAX) return null;
        return StationTileCoord.of(tileDx, tileDy);
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
        ModuleShape shape, Collection<StationTileCoord> pendingTargets) {
        if (!shape.fitsAt(coord)) return false;
        StationTileCoord[] footprint = shape.tiles(coord);
        for (StationTileCoord tile : footprint) {
            if (facility.stationLayout()
                .isOccupied(tile)) return false;
            if (pendingTargets != null && overlapsPendingFootprint(pendingTargets, shape, tile)) return false;
        }
        if (ModuleFootprint.validate(facility.stationLayout(), coord, shape) == ShapeValidation.OK) return true;
        if (pendingTargets == null) return false;
        return hasPendingOrthogonalNeighbour(pendingTargets, coord, shape);
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets,
        StationTileCoord coord) {
        for (StationTileCoord pending : pendingTargets) {
            if (coord.isOrthogonallyAdjacent(pending)) return true;
        }
        return false;
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets,
        StationTileCoord coord, ModuleShape shape) {
        for (StationTileCoord tile : shape.tiles(coord)) {
            if (hasPendingOrthogonalNeighbour(pendingTargets, shape, tile)) return true;
        }
        return false;
    }

    private static boolean hasPendingOrthogonalNeighbour(Collection<StationTileCoord> pendingTargets, ModuleShape shape,
        StationTileCoord tile) {
        for (StationTileCoord pending : pendingTargets) {
            for (StationTileCoord pendingTile : shape.tiles(pending)) {
                if (tile.isOrthogonallyAdjacent(pendingTile)) return true;
            }
        }
        return false;
    }

    private static boolean overlapsPendingFootprint(Collection<StationTileCoord> pendingTargets, ModuleShape shape,
        StationTileCoord tile) {
        for (StationTileCoord pending : pendingTargets) {
            for (StationTileCoord pendingTile : shape.tiles(pending)) {
                if (tile.equals(pendingTile)) return true;
            }
        }
        return false;
    }

    private static boolean hasBuiltOrthogonalNeighbour(AutomatedFacility facility, StationTileCoord coord,
        ModuleShape shape) {
        for (StationTileCoord tile : shape.tiles(coord)) {
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
}
