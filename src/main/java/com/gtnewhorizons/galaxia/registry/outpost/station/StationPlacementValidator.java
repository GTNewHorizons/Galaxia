package com.gtnewhorizons.galaxia.registry.outpost.station;

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
