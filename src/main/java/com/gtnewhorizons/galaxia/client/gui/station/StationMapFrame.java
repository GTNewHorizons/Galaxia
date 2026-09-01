package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Collection;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public record StationMapFrame(int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding,
    int contentVerticalPadding, int panX, int panY) {

    public static final int TILE_SIZE = 24;
    public static final int CONNECTOR_GAP = 4;
    public static final int TILE_STEP = TILE_SIZE + CONNECTOR_GAP;
    public static final int CONNECTOR_OVERLAP = 2;
    public static final int CONNECTOR_SIZE = CONNECTOR_GAP + 2 * CONNECTOR_OVERLAP;

    public record TilePosition(int dx, int dy) {}

    public boolean contains(int localX, int localY) {
        return localX >= contentLeft && localX < widgetWidth - contentRightPadding
            && localY >= contentVerticalPadding
            && localY < widgetHeight - contentVerticalPadding;
    }

    public int tileLocalX(StationTileCoord coord) {
        return tileLocalX(coord.dx());
    }

    public int tileLocalY(StationTileCoord coord) {
        return tileLocalY(coord.dy());
    }

    public int tileLocalX(int dx) {
        return originLocalX() + dx * TILE_STEP;
    }

    public int tileLocalY(int dy) {
        return originLocalY() + dy * TILE_STEP;
    }

    public int connectorLocalX(StationTileCoord left) {
        return tileLocalX(left) + TILE_SIZE - CONNECTOR_OVERLAP;
    }

    public int connectorLocalY(StationTileCoord top) {
        return tileLocalY(top) + TILE_SIZE - CONNECTOR_OVERLAP;
    }

    public @Nullable StationTileCoord coordAt(int localX, int localY) {
        TilePosition position = tilePositionAt(localX, localY);
        if (position == null) return null;
        int dx = position.dx();
        int dy = position.dy();
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return null;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return null;
        return StationTileCoord.of(dx, dy);
    }

    public @Nullable TilePosition tilePositionAt(int localX, int localY) {
        if (!contains(localX, localY)) return null;
        int relX = localX - originLocalX();
        int relY = localY - originLocalY();
        int dx = Math.floorDiv(relX, TILE_STEP);
        int dy = Math.floorDiv(relY, TILE_STEP);
        int inTileX = relX - dx * TILE_STEP;
        int inTileY = relY - dy * TILE_STEP;
        if (inTileX < 0 || inTileX >= TILE_SIZE || inTileY < 0 || inTileY >= TILE_SIZE) return null;
        return new TilePosition(dx, dy);
    }

    public void collectVisibleTilePositions(Collection<TilePosition> result) {
        result.clear();
        int right = widgetWidth - contentRightPadding;
        int bottom = widgetHeight - contentVerticalPadding;
        if (right <= contentLeft || bottom <= contentVerticalPadding) return;

        int originX = originLocalX();
        int originY = originLocalY();
        int minDx = Math.floorDiv(contentLeft - originX - TILE_SIZE + 1, TILE_STEP);
        int maxDx = Math.floorDiv(right - 1 - originX, TILE_STEP);
        int minDy = Math.floorDiv(contentVerticalPadding - originY - TILE_SIZE + 1, TILE_STEP);
        int maxDy = Math.floorDiv(bottom - 1 - originY, TILE_STEP);

        for (int dy = minDy; dy <= maxDy; dy++) {
            for (int dx = minDx; dx <= maxDx; dx++) {
                result.add(new TilePosition(dx, dy));
            }
        }
    }

    public boolean tileIntersectsScreen(int tileX, int tileY) {
        return tileX < widgetWidth && tileX + TILE_SIZE > 0 && tileY < widgetHeight && tileY + TILE_SIZE > 0;
    }

    private int originLocalX() {
        int availableWidth = Math.max(TILE_STEP, widgetWidth - contentLeft - contentRightPadding);
        return contentLeft + availableWidth / 2 - TILE_SIZE / 2 + panX;
    }

    private int originLocalY() {
        int availableHeight = Math.max(TILE_STEP, widgetHeight - contentVerticalPadding * 2);
        return contentVerticalPadding + availableHeight / 2 - TILE_SIZE / 2 + panY;
    }
}
