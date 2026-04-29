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
        StationTileCoord[] result = new StationTileCoord[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            result[i] = StationTileCoord.of(anchor.dx() + offsets[i][0], anchor.dy() + offsets[i][1]);
        }
        return result;
    }

    public boolean fitsAt(StationTileCoord anchor) {
        return switch (this) {
            case SINGLE -> true;
            case QUAD_2x2 -> anchor.dx() + 1 <= StationTileCoord.MAX && anchor.dy() + 1 <= StationTileCoord.MAX;
            case BLOCK_3x3 -> anchor.dx() - 1 >= StationTileCoord.MIN && anchor.dx() + 1 <= StationTileCoord.MAX
                && anchor.dy() - 1 >= StationTileCoord.MIN
                && anchor.dy() + 1 <= StationTileCoord.MAX;
        };
    }
}
