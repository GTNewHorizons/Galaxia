package com.gtnewhorizons.galaxia.registry.outpost.station;

public final class ModuleFootprint {

    private ModuleFootprint() {}

    public static ShapeValidation validate(StationLayout layout, StationTileCoord anchor, ModuleShape shape) {
        if (!shape.fitsAt(anchor)) return ShapeValidation.OUT_OF_BOUNDS;
        StationTileCoord[] tiles = shape.tiles(anchor);
        boolean hasAdjacent = false;
        for (StationTileCoord tile : tiles) {
            if (layout.isOccupied(tile)) return ShapeValidation.OVERLAP;
            if (!hasAdjacent && hasOccupiedNeighbor(layout, tile)) hasAdjacent = true;
        }
        return hasAdjacent ? ShapeValidation.OK : ShapeValidation.NOT_ADJACENT;
    }

    private static boolean hasOccupiedNeighbor(StationLayout layout, StationTileCoord tile) {
        int dx = tile.dx();
        int dy = tile.dy();
        if (dx > StationTileCoord.MIN && layout.isOccupied(StationTileCoord.of(dx - 1, dy))) return true;
        if (dx < StationTileCoord.MAX && layout.isOccupied(StationTileCoord.of(dx + 1, dy))) return true;
        if (dy > StationTileCoord.MIN && layout.isOccupied(StationTileCoord.of(dx, dy - 1))) return true;
        if (dy < StationTileCoord.MAX && layout.isOccupied(StationTileCoord.of(dx, dy + 1))) return true;
        return false;
    }
}
