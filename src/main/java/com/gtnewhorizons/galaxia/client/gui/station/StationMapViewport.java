package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationMapViewport {

    public static final int TILE_SIZE = 24;
    public static final int CONNECTOR_GAP = 4;
    public static final int TILE_STEP = TILE_SIZE + CONNECTOR_GAP;
    public static final int CONNECTOR_OVERLAP = 2;

    private StationMapViewport() {}

    public record TilePosition(int dx, int dy) {}

    public static boolean contains(int localX, int localY, int width, int height, int contentLeft,
        int contentRightPadding, int contentVerticalPadding) {
        return localX >= contentLeft && localX < width - contentRightPadding
            && localY >= contentVerticalPadding
            && localY < height - contentVerticalPadding;
    }

    public static int originLocalX(int width, int contentLeft, int contentRightPadding) {
        return originLocalX(width, contentLeft, contentRightPadding, 0);
    }

    public static int originLocalX(int width, int contentLeft, int contentRightPadding, int panX) {
        int availableWidth = Math.max(TILE_STEP, width - contentLeft - contentRightPadding);
        return contentLeft + availableWidth / 2 - TILE_SIZE / 2 + panX;
    }

    public static int originLocalY(int height, int contentVerticalPadding) {
        return originLocalY(height, contentVerticalPadding, 0);
    }

    public static int originLocalY(int height, int contentVerticalPadding, int panY) {
        int availableHeight = Math.max(TILE_STEP, height - contentVerticalPadding * 2);
        return contentVerticalPadding + availableHeight / 2 - TILE_SIZE / 2 + panY;
    }

    public static int tileLeftX(StationTileCoord coord, int width, int contentLeft, int contentRightPadding) {
        return tileLeftX(coord, width, contentLeft, contentRightPadding, 0);
    }

    public static int tileLeftX(StationTileCoord coord, int width, int contentLeft, int contentRightPadding, int panX) {
        return tileLeftX(coord.dx(), width, contentLeft, contentRightPadding, panX);
    }

    public static int tileLeftX(int dx, int width, int contentLeft, int contentRightPadding, int panX) {
        return originLocalX(width, contentLeft, contentRightPadding, panX) + dx * TILE_STEP;
    }

    public static int tileTopY(StationTileCoord coord, int height, int contentVerticalPadding) {
        return tileTopY(coord, height, contentVerticalPadding, 0);
    }

    public static int tileTopY(StationTileCoord coord, int height, int contentVerticalPadding, int panY) {
        return tileTopY(coord.dy(), height, contentVerticalPadding, panY);
    }

    public static int tileTopY(int dy, int height, int contentVerticalPadding, int panY) {
        return originLocalY(height, contentVerticalPadding, panY) + dy * TILE_STEP;
    }

    public static int connectorLeftX(StationTileCoord coord, int width, int contentLeft, int contentRightPadding,
        int panX) {
        return tileLeftX(coord, width, contentLeft, contentRightPadding, panX) + TILE_SIZE - CONNECTOR_OVERLAP;
    }

    public static int connectorTopY(StationTileCoord coord, int height, int contentVerticalPadding, int panY) {
        return tileTopY(coord, height, contentVerticalPadding, panY) + TILE_SIZE - CONNECTOR_OVERLAP;
    }

    public static int connectorWidth() {
        return CONNECTOR_GAP + 2 * CONNECTOR_OVERLAP;
    }

    public static int connectorHeight() {
        return CONNECTOR_GAP + 2 * CONNECTOR_OVERLAP;
    }

    public static @Nullable StationTileCoord coordAt(int localX, int localY, int width, int height, int contentLeft,
        int contentRightPadding, int contentVerticalPadding) {
        return coordAt(localX, localY, width, height, contentLeft, contentRightPadding, contentVerticalPadding, 0, 0);
    }

    public static @Nullable StationTileCoord coordAt(int localX, int localY, int width, int height, int contentLeft,
        int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        TilePosition position = tilePositionAt(
            localX,
            localY,
            width,
            height,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
        if (position == null) return null;
        int dx = position.dx();
        int dy = position.dy();
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return null;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return null;
        return StationTileCoord.of(dx, dy);
    }

    public static @Nullable TilePosition tilePositionAt(int localX, int localY, int width, int height, int contentLeft,
        int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (!contains(localX, localY, width, height, contentLeft, contentRightPadding, contentVerticalPadding))
            return null;
        int relX = localX - originLocalX(width, contentLeft, contentRightPadding, panX);
        int relY = localY - originLocalY(height, contentVerticalPadding, panY);
        int dx = Math.floorDiv(relX, TILE_STEP);
        int dy = Math.floorDiv(relY, TILE_STEP);
        int inTileX = relX - dx * TILE_STEP;
        int inTileY = relY - dy * TILE_STEP;
        if (inTileX < 0 || inTileX >= TILE_SIZE || inTileY < 0 || inTileY >= TILE_SIZE) return null;
        return new TilePosition(dx, dy);
    }

    public static Set<StationTileCoord> visibleTiles(int width, int height, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, int panX, int panY) {
        Set<StationTileCoord> result = new LinkedHashSet<>();
        int left = contentLeft;
        int right = width - contentRightPadding;
        int top = contentVerticalPadding;
        int bottom = height - contentVerticalPadding;
        if (right <= left || bottom <= top) return result;

        int originX = originLocalX(width, contentLeft, contentRightPadding, panX);
        int originY = originLocalY(height, contentVerticalPadding, panY);
        int minDx = Math.floorDiv(left - originX - TILE_SIZE + 1, TILE_STEP);
        int maxDx = Math.floorDiv(right - 1 - originX, TILE_STEP);
        int minDy = Math.floorDiv(top - originY - TILE_SIZE + 1, TILE_STEP);
        int maxDy = Math.floorDiv(bottom - 1 - originY, TILE_STEP);

        minDx = Math.max(minDx, StationTileCoord.MIN);
        maxDx = Math.min(maxDx, StationTileCoord.MAX);
        minDy = Math.max(minDy, StationTileCoord.MIN);
        maxDy = Math.min(maxDy, StationTileCoord.MAX);
        for (int dy = minDy; dy <= maxDy; dy++) {
            for (int dx = minDx; dx <= maxDx; dx++) {
                result.add(StationTileCoord.of(dx, dy));
            }
        }
        return result;
    }

    public static Set<TilePosition> visibleTilePositions(int width, int height, int contentLeft,
        int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        Set<TilePosition> result = new LinkedHashSet<>();
        collectVisibleTilePositions(
            width,
            height,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY,
            result);
        return result;
    }

    public static void collectVisibleTilePositions(int width, int height, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, int panX, int panY, Collection<TilePosition> result) {
        result.clear();
        int left = contentLeft;
        int right = width - contentRightPadding;
        int top = contentVerticalPadding;
        int bottom = height - contentVerticalPadding;
        if (right <= left || bottom <= top) return;

        int originX = originLocalX(width, contentLeft, contentRightPadding, panX);
        int originY = originLocalY(height, contentVerticalPadding, panY);
        int minDx = Math.floorDiv(left - originX - TILE_SIZE + 1, TILE_STEP);
        int maxDx = Math.floorDiv(right - 1 - originX, TILE_STEP);
        int minDy = Math.floorDiv(top - originY - TILE_SIZE + 1, TILE_STEP);
        int maxDy = Math.floorDiv(bottom - 1 - originY, TILE_STEP);

        for (int dy = minDy; dy <= maxDy; dy++) {
            for (int dx = minDx; dx <= maxDx; dx++) {
                result.add(new TilePosition(dx, dy));
            }
        }
    }
}
