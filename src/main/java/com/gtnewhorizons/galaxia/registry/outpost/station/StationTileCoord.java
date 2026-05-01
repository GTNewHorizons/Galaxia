package com.gtnewhorizons.galaxia.registry.outpost.station;

public record StationTileCoord(byte dx, byte dy) implements Comparable<StationTileCoord> {

    public static final StationTileCoord CORE = new StationTileCoord((byte) 0, (byte) 0);

    public static final byte MIN = -31;
    public static final byte MAX = 31;

    public StationTileCoord {
        if (dx < MIN || dx > MAX || dy < MIN || dy > MAX) {
            throw new IllegalArgumentException("StationTileCoord out of range: " + dx + "," + dy);
        }
    }

    public static StationTileCoord of(int dx, int dy) {
        if (dx < MIN || dx > MAX || dy < MIN || dy > MAX) {
            throw new IllegalArgumentException("StationTileCoord out of range: " + dx + "," + dy);
        }
        return new StationTileCoord((byte) dx, (byte) dy);
    }

    public boolean isOrthogonallyAdjacent(StationTileCoord other) {
        int adx = Math.abs(this.dx - other.dx);
        int ady = Math.abs(this.dy - other.dy);
        return (adx == 1 && ady == 0) || (adx == 0 && ady == 1);
    }

    @Override
    public int compareTo(StationTileCoord other) {
        int cmp = Byte.compare(this.dx, other.dx);
        if (cmp != 0) return cmp;
        return Byte.compare(this.dy, other.dy);
    }
}
