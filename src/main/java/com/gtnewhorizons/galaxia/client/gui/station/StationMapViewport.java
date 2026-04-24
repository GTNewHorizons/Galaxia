package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapViewport {

    private StationMapViewport() {}

    static boolean contains(int localX, int localY, int width, int height, int contentLeft, int contentRightPadding,
        int contentVerticalPadding) {
        return localX >= contentLeft && localX < width - contentRightPadding
            && localY >= contentVerticalPadding
            && localY < height - contentVerticalPadding;
    }

    static int originLocalX(int width, int tileSize, int contentLeft, int contentRightPadding) {
        return originLocalX(width, tileSize, contentLeft, contentRightPadding, 0);
    }

    static int originLocalX(int width, int tileSize, int contentLeft, int contentRightPadding, int panX) {
        int availableWidth = Math.max(tileSize, width - contentLeft - contentRightPadding);
        return contentLeft + availableWidth / 2 - tileSize / 2 + panX;
    }

    static int originLocalY(int height, int tileSize, int contentVerticalPadding) {
        return originLocalY(height, tileSize, contentVerticalPadding, 0);
    }

    static int originLocalY(int height, int tileSize, int contentVerticalPadding, int panY) {
        int availableHeight = Math.max(tileSize, height - contentVerticalPadding * 2);
        return contentVerticalPadding + availableHeight / 2 - tileSize / 2 + panY;
    }

    static int tileLeftX(StationTileCoord coord, int width, int tileSize, int contentLeft, int contentRightPadding) {
        return tileLeftX(coord, width, tileSize, contentLeft, contentRightPadding, 0);
    }

    static int tileLeftX(StationTileCoord coord, int width, int tileSize, int contentLeft, int contentRightPadding,
        int panX) {
        return originLocalX(width, tileSize, contentLeft, contentRightPadding, panX) + coord.dx() * tileSize;
    }

    static int tileTopY(StationTileCoord coord, int height, int tileSize, int contentVerticalPadding) {
        return tileTopY(coord, height, tileSize, contentVerticalPadding, 0);
    }

    static int tileTopY(StationTileCoord coord, int height, int tileSize, int contentVerticalPadding, int panY) {
        return originLocalY(height, tileSize, contentVerticalPadding, panY) + coord.dy() * tileSize;
    }

    static @Nullable StationTileCoord coordAt(int localX, int localY, int width, int height, int tileSize,
        int contentLeft, int contentRightPadding, int contentVerticalPadding) {
        return coordAt(
            localX,
            localY,
            width,
            height,
            tileSize,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            0,
            0);
    }

    static @Nullable StationTileCoord coordAt(int localX, int localY, int width, int height, int tileSize,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (!contains(localX, localY, width, height, contentLeft, contentRightPadding, contentVerticalPadding))
            return null;
        int relX = localX - originLocalX(width, tileSize, contentLeft, contentRightPadding, panX);
        int relY = localY - originLocalY(height, tileSize, contentVerticalPadding, panY);
        int dx = Math.floorDiv(relX, tileSize);
        int dy = Math.floorDiv(relY, tileSize);
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return null;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return null;
        return StationTileCoord.of(dx, dy);
    }
}
