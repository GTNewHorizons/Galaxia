package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.ArrayList;
import java.util.List;

public enum ModuleShape {

    SINGLE,
    QUAD_2x2,
    BLOCK_3x3;

    private static final ModuleShape[] VALUES = values();

    public byte toByte() {
        return (byte) ordinal();
    }

    public static ModuleShape fromByte(byte ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return SINGLE;
        return VALUES[ordinal];
    }

    public int tileCount() {
        return switch (this) {
            case SINGLE -> 1;
            case QUAD_2x2 -> 4;
            case BLOCK_3x3 -> 9;
        };
    }

    public boolean coversAnchor() {
        return true;
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

    public Iterable<StationTileCoord> tiles(StationTileCoord anchor) {
        return switch (this) {
            case SINGLE -> List.of(anchor);
            case QUAD_2x2 -> List.of(
                anchor,
                StationTileCoord.of(anchor.dx() + 1, anchor.dy()),
                StationTileCoord.of(anchor.dx(), anchor.dy() + 1),
                StationTileCoord.of(anchor.dx() + 1, anchor.dy() + 1));
            case BLOCK_3x3 -> {
                List<StationTileCoord> tiles = new ArrayList<>(9);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        tiles.add(StationTileCoord.of(anchor.dx() + dx, anchor.dy() + dy));
                    }
                }
                yield tiles;
            }
        };
    }
}
