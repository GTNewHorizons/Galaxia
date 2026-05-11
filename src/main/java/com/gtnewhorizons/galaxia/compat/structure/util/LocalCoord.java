package com.gtnewhorizons.galaxia.compat.structure.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public final class LocalCoord {

    public static final int SEARCH_RADIUS = 16;
    public static final int BITS_PER_COORD = 10;
    public static final int MAX_OFFSET = (1 << BITS_PER_COORD) - 1;
    public static final int MAX_SEARCH_RADIUS = MAX_OFFSET / 2;

    private LocalCoord() {}

    private static int offset(int v, int searchRadius) {
        return v + searchRadius;
    }

    private static int unoffset(int v, int searchRadius) {
        return v - searchRadius;
    }

    public static int pack(int x, int y, int z, int searchRadius) {
        if (searchRadius > MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("Search radius too large for 10-bit encoding: " + searchRadius);
        }
        int ox = offset(x, searchRadius);
        int oy = offset(y, searchRadius);
        int oz = offset(z, searchRadius);
        validateOffset(ox);
        validateOffset(oy);
        validateOffset(oz);
        return (ox << 20) | (oy << 10) | oz;
    }

    private static void validateOffset(int offset) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("Coordinate offset out of bounds: " + offset);
        }
    }

    public static int unpackX(int v, int searchRadius) {
        return unoffset((v >> 20) & MAX_OFFSET, searchRadius);
    }

    public static int unpackY(int v, int searchRadius) {
        return unoffset((v >> 10) & MAX_OFFSET, searchRadius);
    }

    public static int unpackZ(int v, int searchRadius) {
        return unoffset(v & MAX_OFFSET, searchRadius);
    }

    public static int unpackSignedY(int v, int searchRadius) {
        return unpackY(v, searchRadius);
    }

    public static int packFromWorld(int wx, int wy, int wz, int xCoord, int yCoord, int zCoord, int searchRadius) {
        return pack(wx - xCoord, wy - yCoord, wz - zCoord, searchRadius);
    }

    public static boolean isInBounds(int x, int y, int z, int searchRadius) {
        return x >= -searchRadius && x <= searchRadius
            && y >= -searchRadius
            && y <= searchRadius
            && z >= -searchRadius
            && z <= searchRadius;
    }

    public static int worldX(int localX, int xCoord) {
        return localX + xCoord;
    }

    public static int worldY(int localY, int yCoord) {
        return localY + yCoord;
    }

    public static int worldZ(int localZ, int zCoord) {
        return localZ + zCoord;
    }

    public static IntSet newBlockSet() {
        return new IntOpenHashSet();
    }
}
