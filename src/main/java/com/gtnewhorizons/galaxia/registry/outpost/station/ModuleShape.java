package com.gtnewhorizons.galaxia.registry.outpost.station;

public enum ModuleShape {

    SINGLE(new byte[][] { { 0, 0 } }),
    QUAD_2x2(new byte[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } }),
    BLOCK_3x3(new byte[][] { { -1, -1 }, { 0, -1 }, { 1, -1 }, { -1, 0 }, { 0, 0 }, { 1, 0 }, { -1, 1 }, { 0, 1 },
        { 1, 1 } }),
    L_2x2(new byte[][] { { 0, 0 }, { 0, 1 }, { 1, 1 } });

    private final byte[][] offsets;
    private final int minDx;
    private final int minDy;
    private final int textureGridWidth;
    private final int textureGridHeight;

    ModuleShape(byte[][] offsets) {
        this.offsets = offsets;
        int minX = 0;
        int minY = 0;
        int maxX = 0;
        int maxY = 0;
        for (byte[] offset : offsets) {
            minX = Math.min(minX, offset[0]);
            minY = Math.min(minY, offset[1]);
            maxX = Math.max(maxX, offset[0]);
            maxY = Math.max(maxY, offset[1]);
        }
        this.minDx = minX;
        this.minDy = minY;
        this.textureGridWidth = maxX - minX + 1;
        this.textureGridHeight = maxY - minY + 1;
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

    public int textureGridWidth() {
        return textureGridWidth;
    }

    public int textureGridHeight() {
        return textureGridHeight;
    }

    public TextureTile textureTile(StationTileCoord anchor, StationTileCoord tile, int rotation) {
        int dx = tile.dx() - anchor.dx();
        int dy = tile.dy() - anchor.dy();
        int baseDx;
        int baseDy;
        switch (normalizeRotation(rotation)) {
            case 1 -> {
                baseDx = dy;
                baseDy = -dx;
            }
            case 2 -> {
                baseDx = -dx;
                baseDy = -dy;
            }
            case 3 -> {
                baseDx = -dy;
                baseDy = dx;
            }
            default -> {
                baseDx = dx;
                baseDy = dy;
            }
        }
        return new TextureTile(baseDx - minDx, baseDy - minDy);
    }

    public static int normalizeRotation(int rotation) {
        return Math.floorMod(rotation, 4);
    }

    public record TextureTile(int column, int row) {}
}
