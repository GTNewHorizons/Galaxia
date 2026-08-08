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
            RotatedOffset offset = rotatedOffset(offsets[i], normalizedRotation);
            result[i] = StationTileCoord.of(anchor.dx() + offset.dx(), anchor.dy() + offset.dy());
        }
        return result;
    }

    public boolean fitsAt(StationTileCoord anchor) {
        return fitsAt(anchor, 0);
    }

    public boolean fitsAt(StationTileCoord anchor, int rotation) {
        int normalizedRotation = normalizeRotation(rotation);
        for (byte[] baseOffset : offsets) {
            RotatedOffset offset = rotatedOffset(baseOffset, normalizedRotation);
            int dx = anchor.dx() + offset.dx();
            int dy = anchor.dy() + offset.dy();
            if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return false;
            if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return false;
        }
        return true;
    }

    private static RotatedOffset rotatedOffset(byte[] offset, int normalizedRotation) {
        int dx = offset[0];
        int dy = offset[1];
        return switch (normalizedRotation) {
            case 1 -> new RotatedOffset(-dy, dx);
            case 2 -> new RotatedOffset(-dx, -dy);
            case 3 -> new RotatedOffset(dy, -dx);
            default -> new RotatedOffset(dx, dy);
        };
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

    private record RotatedOffset(int dx, int dy) {}

    public record TextureTile(int column, int row) {}
}
