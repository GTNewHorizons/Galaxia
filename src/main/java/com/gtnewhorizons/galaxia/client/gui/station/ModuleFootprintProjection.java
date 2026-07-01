package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleFootprintProjection {

    private ModuleFootprintProjection() {}

    public static List<Segment> filledSegments(ModuleShape shape, StationTileCoord anchor, int rotation,
        int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding, int contentVerticalPadding,
        int panX, int panY) {
        StationTileCoord[] tiles = shape.tiles(anchor, rotation);
        Set<StationTileCoord> occupied = new HashSet<>();
        for (StationTileCoord tile : tiles) {
            occupied.add(tile);
        }
        List<Segment> segments = new ArrayList<>();
        for (StationTileCoord tile : tiles) {
            int x = StationMapViewport.tileLeftX(tile.dx(), widgetWidth, contentLeft, contentRightPadding, panX);
            int y = StationMapViewport.tileTopY(tile.dy(), widgetHeight, contentVerticalPadding, panY);
            segments.add(new Segment(x, y, StationMapViewport.TILE_SIZE, StationMapViewport.TILE_SIZE));
            if (isOccupied(occupied, tile.dx() + 1, tile.dy())) {
                segments.add(
                    new Segment(
                        x + StationMapViewport.TILE_SIZE,
                        y,
                        StationMapViewport.CONNECTOR_GAP,
                        StationMapViewport.TILE_SIZE));
            }
            if (isOccupied(occupied, tile.dx(), tile.dy() + 1)) {
                segments.add(
                    new Segment(
                        x,
                        y + StationMapViewport.TILE_SIZE,
                        StationMapViewport.TILE_SIZE,
                        StationMapViewport.CONNECTOR_GAP));
            }
            if (isOccupied(occupied, tile.dx() + 1, tile.dy()) && isOccupied(occupied, tile.dx(), tile.dy() + 1)
                && isOccupied(occupied, tile.dx() + 1, tile.dy() + 1)) {
                segments.add(
                    new Segment(
                        x + StationMapViewport.TILE_SIZE,
                        y + StationMapViewport.TILE_SIZE,
                        StationMapViewport.CONNECTOR_GAP,
                        StationMapViewport.CONNECTOR_GAP));
            }
        }
        return segments;
    }

    public static List<Segment> outlineSegments(ModuleShape shape, StationTileCoord anchor, int rotation,
        int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding, int contentVerticalPadding,
        int panX, int panY) {
        List<Segment> filledSegments = filledSegments(
            shape,
            anchor,
            rotation,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
        if (filledSegments.isEmpty()) return List.of();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Segment segment : filledSegments) {
            minX = Math.min(minX, segment.x());
            minY = Math.min(minY, segment.y());
            maxX = Math.max(maxX, segment.x() + segment.width());
            maxY = Math.max(maxY, segment.y() + segment.height());
        }

        boolean[][] filled = new boolean[maxY - minY][maxX - minX];
        for (Segment segment : filledSegments) {
            markRect(filled, segment.x() - minX, segment.y() - minY, segment.width(), segment.height());
        }

        List<Segment> outline = new ArrayList<>();
        addHorizontalOutlineSegments(outline, filled, minX, minY, true);
        addHorizontalOutlineSegments(outline, filled, minX, minY, false);
        addVerticalOutlineSegments(outline, filled, minX, minY, true);
        addVerticalOutlineSegments(outline, filled, minX, minY, false);
        addConcaveCornerOutlineSegments(outline, filled, minX, minY);
        return outline;
    }

    public static boolean contains(ModuleShape shape, StationTileCoord anchor, int rotation, int x, int y,
        int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding, int contentVerticalPadding,
        int panX, int panY) {
        for (Segment segment : filledSegments(
            shape,
            anchor,
            rotation,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY)) {
            if (segment.contains(x, y)) return true;
        }
        return false;
    }

    private static boolean isOccupied(Set<StationTileCoord> occupied, int dx, int dy) {
        return occupied.contains(StationTileCoord.of(dx, dy));
    }

    private static void markRect(boolean[][] filled, int x, int y, int width, int height) {
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) {
                filled[py][px] = true;
            }
        }
    }

    private static void addHorizontalOutlineSegments(List<Segment> segments, boolean[][] filled, int baseX, int baseY,
        boolean top) {
        int height = filled.length;
        int width = filled[0].length;
        for (int y = 0; y < height; y++) {
            int runStart = -1;
            for (int x = 0; x <= width; x++) {
                boolean edge = x < width && filled[y][x]
                    && (top ? y == 0 || !filled[y - 1][x] : y == height - 1 || !filled[y + 1][x]);
                if (edge && runStart < 0) {
                    runStart = x;
                } else if (!edge && runStart >= 0) {
                    segments.add(new Segment(baseX + runStart, baseY + y, x - runStart, 1));
                    runStart = -1;
                }
            }
        }
    }

    private static void addVerticalOutlineSegments(List<Segment> segments, boolean[][] filled, int baseX, int baseY,
        boolean left) {
        int height = filled.length;
        int width = filled[0].length;
        for (int x = 0; x < width; x++) {
            int runStart = -1;
            for (int y = 0; y <= height; y++) {
                boolean edge = y < height && filled[y][x]
                    && (left ? x == 0 || !filled[y][x - 1] : x == width - 1 || !filled[y][x + 1]);
                if (edge && runStart < 0) {
                    runStart = y;
                } else if (!edge && runStart >= 0) {
                    segments.add(new Segment(baseX + x, baseY + runStart, 1, y - runStart));
                    runStart = -1;
                }
            }
        }
    }

    private static void addConcaveCornerOutlineSegments(List<Segment> segments, boolean[][] filled, int baseX,
        int baseY) {
        int height = filled.length;
        int width = filled[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!filled[y][x]) continue;
                if (isConcaveCorner(filled, x, y, 1, -1) || isConcaveCorner(filled, x, y, -1, -1)
                    || isConcaveCorner(filled, x, y, 1, 1)
                    || isConcaveCorner(filled, x, y, -1, 1)) {
                    segments.add(new Segment(baseX + x, baseY + y, 1, 1));
                }
            }
        }
    }

    private static boolean isConcaveCorner(boolean[][] filled, int x, int y, int dx, int dy) {
        return isFilled(filled, x + dx, y) && isFilled(filled, x, y + dy) && !isFilled(filled, x + dx, y + dy);
    }

    private static boolean isFilled(boolean[][] filled, int x, int y) {
        return y >= 0 && y < filled.length && x >= 0 && x < filled[0].length && filled[y][x];
    }

    public record Segment(int x, int y, int width, int height) {

        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
