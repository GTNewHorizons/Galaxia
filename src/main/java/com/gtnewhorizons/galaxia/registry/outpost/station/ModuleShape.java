package com.gtnewhorizons.galaxia.registry.outpost.station;

public enum ModuleShape {

    SINGLE(new byte[][] { { 0, 0 } }),
    QUAD_2x2(new byte[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } }),
    BLOCK_3x3(new byte[][] { { -1, -1 }, { 0, -1 }, { 1, -1 }, { -1, 0 }, { 0, 0 }, { 1, 0 }, { -1, 1 }, { 0, 1 },
        { 1, 1 } });

    private final byte[][] offsets;

    ModuleShape(byte[][] offsets) {
        this.offsets = offsets;
    }

    public int tileCount() {
        return offsets.length;
    }

    public StationTileCoord[] tiles(StationTileCoord anchor) {
        return tiles(anchor, 0);
    }

    public StationTileCoord[] tiles(StationTileCoord anchor, int rotation) {
        StationTileCoord[] result = new StationTileCoord[offsets.length];
        int normalizedRotation = normalizeRotation(rotation);
        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0];
            int dy = offsets[i][1];
            int rotatedDx;
            int rotatedDy;
            switch (normalizedRotation) {
                case 1 -> {
                    rotatedDx = -dy;
                    rotatedDy = dx;
                }
                case 2 -> {
                    rotatedDx = -dx;
                    rotatedDy = -dy;
                }
                case 3 -> {
                    rotatedDx = dy;
                    rotatedDy = -dx;
                }
                default -> {
                    rotatedDx = dx;
                    rotatedDy = dy;
                }
            }
            result[i] = StationTileCoord.of(anchor.dx() + rotatedDx, anchor.dy() + rotatedDy);
        }
        return result;
    }

    public boolean fitsAt(StationTileCoord anchor) {
        return fitsAt(anchor, 0);
    }

    public boolean fitsAt(StationTileCoord anchor, int rotation) {
        for (StationTileCoord tile : tiles(anchor, rotation)) {
            if (tile.dx() < StationTileCoord.MIN || tile.dx() > StationTileCoord.MAX
                || tile.dy() < StationTileCoord.MIN
                || tile.dy() > StationTileCoord.MAX) {
                return false;
            }
        }
        return true;
    }

    public static int normalizeRotation(int rotation) {
        return Math.floorMod(rotation, 4);
    }
}
