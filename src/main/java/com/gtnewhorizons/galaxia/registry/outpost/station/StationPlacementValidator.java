package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Set;

public final class StationPlacementValidator {

    private StationPlacementValidator() {}

    public enum Result {

        OK,
        REJECTED_OCCUPIED,
        REJECTED_NOT_ADJACENT
    }

    public static Result validate(StationLayout layout, StationTileCoord coord) {
        if (layout.isOccupied(coord)) return Result.REJECTED_OCCUPIED;
        if (hasOccupiedOrthogonalNeighbour(layout, coord)) return Result.OK;
        return Result.REJECTED_NOT_ADJACENT;
    }

    public static void collectExpansionSlots(StationLayout layout, Set<StationTileCoord> out) {
        out.clear();
        for (StationTileCoord occupied : layout.snapshot()
            .keySet()) {
            addNeighbourIfExpansion(layout, out, occupied.dx() - 1, occupied.dy());
            addNeighbourIfExpansion(layout, out, occupied.dx() + 1, occupied.dy());
            addNeighbourIfExpansion(layout, out, occupied.dx(), occupied.dy() - 1);
            addNeighbourIfExpansion(layout, out, occupied.dx(), occupied.dy() + 1);
        }
    }

    private static void addNeighbourIfExpansion(StationLayout layout, Set<StationTileCoord> out, int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return;
        StationTileCoord coord = StationTileCoord.of(dx, dy);
        if (validate(layout, coord) == Result.OK) out.add(coord);
    }

    private static boolean hasOccupiedOrthogonalNeighbour(StationLayout layout, StationTileCoord coord) {
        return isNeighbourOccupied(layout, coord.dx() - 1, coord.dy())
            || isNeighbourOccupied(layout, coord.dx() + 1, coord.dy())
            || isNeighbourOccupied(layout, coord.dx(), coord.dy() - 1)
            || isNeighbourOccupied(layout, coord.dx(), coord.dy() + 1);
    }

    private static boolean isNeighbourOccupied(StationLayout layout, int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return false;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return false;
        return layout.isOccupied(StationTileCoord.of(dx, dy));
    }
}
