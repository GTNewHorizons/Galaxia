package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.Gui;

import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleFootprintProjection {

    private static final Geometry[][] GEOMETRIES = buildGeometries();

    private ModuleFootprintProjection() {}

    private record Geometry(List<Segment> filled, List<Segment> outline, StationTileCoord firstTile, Segment bounds) {}

    private static Geometry[][] buildGeometries() {
        ModuleShape[] shapes = ModuleShape.values();
        Geometry[][] geometries = new Geometry[shapes.length][4];
        for (ModuleShape shape : shapes) {
            for (int rotation = 0; rotation < 4; rotation++) {
                List<Segment> filled = List.copyOf(buildFilledSegments(shape, rotation));
                StationTileCoord firstTile = StationTileCoord.CORE;
                for (StationTileCoord tile : shape.tiles(StationTileCoord.CORE, rotation)) {
                    if (tile.dy() < firstTile.dy() || tile.dy() == firstTile.dy() && tile.dx() < firstTile.dx()) {
                        firstTile = tile;
                    }
                }
                Segment bounds = boundsOf(filled);
                geometries[shape.ordinal()][rotation] = new Geometry(
                    filled,
                    List.copyOf(buildOutlineSegments(filled, bounds)),
                    firstTile,
                    bounds);
            }
        }
        return geometries;
    }

    private static Geometry geometry(ModuleShape shape, StationTileCoord anchor, int rotation) {
        if (!shape.fitsAt(anchor, rotation)) throw new IllegalArgumentException("Footprint extends beyond map bounds");
        return GEOMETRIES[shape.ordinal()][ModuleShape.normalizeRotation(rotation)];
    }

    private static List<Segment> translated(List<Segment> segments, StationTileCoord anchor, StationMapFrame frame) {
        int x = frame.tileLocalX(anchor);
        int y = frame.tileLocalY(anchor);
        List<Segment> translated = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            translated.add(new Segment(x + segment.x(), y + segment.y(), segment.width(), segment.height()));
        }
        return translated;
    }

    public static List<Segment> filledSegments(ModuleShape shape, StationTileCoord anchor, int rotation,
        StationMapFrame frame) {
        return translated(geometry(shape, anchor, rotation).filled(), anchor, frame);
    }

    public static StationTileCoord firstTile(ModuleShape shape, StationTileCoord anchor, int rotation) {
        StationTileCoord offset = geometry(shape, anchor, rotation).firstTile();
        return StationTileCoord.of(anchor.dx() + offset.dx(), anchor.dy() + offset.dy());
    }

    public static Segment bounds(ModuleShape shape, StationTileCoord anchor, int rotation, StationMapFrame frame) {
        Segment bounds = geometry(shape, anchor, rotation).bounds();
        return new Segment(
            frame.tileLocalX(anchor) + bounds.x(),
            frame.tileLocalY(anchor) + bounds.y(),
            bounds.width(),
            bounds.height());
    }

    public static void drawFilled(ModuleShape shape, StationTileCoord anchor, int rotation, StationMapFrame frame,
        int color) {
        draw(geometry(shape, anchor, rotation).filled(), anchor, frame, color);
    }

    public static void drawOutline(ModuleShape shape, StationTileCoord anchor, int rotation, StationMapFrame frame,
        int color) {
        draw(geometry(shape, anchor, rotation).outline(), anchor, frame, color);
    }

    private static void draw(List<Segment> segments, StationTileCoord anchor, StationMapFrame frame, int color) {
        int x = frame.tileLocalX(anchor);
        int y = frame.tileLocalY(anchor);
        for (Segment segment : segments) {
            Gui.drawRect(
                x + segment.x(),
                y + segment.y(),
                x + segment.x() + segment.width(),
                y + segment.y() + segment.height(),
                color);
        }
    }

    private static List<Segment> buildFilledSegments(ModuleShape shape, int rotation) {
        StationTileCoord[] tiles = shape.tiles(StationTileCoord.CORE, rotation);
        Set<StationTileCoord> occupied = new HashSet<>();
        for (StationTileCoord tile : tiles) {
            occupied.add(tile);
        }
        List<Segment> segments = new ArrayList<>();
        for (StationTileCoord tile : tiles) {
            int x = tile.dx() * StationMapFrame.TILE_STEP;
            int y = tile.dy() * StationMapFrame.TILE_STEP;
            segments.add(new Segment(x, y, StationMapFrame.TILE_SIZE, StationMapFrame.TILE_SIZE));
            if (isOccupied(occupied, tile.dx() + 1, tile.dy())) {
                segments.add(
                    new Segment(
                        x + StationMapFrame.TILE_SIZE,
                        y,
                        StationMapFrame.CONNECTOR_GAP,
                        StationMapFrame.TILE_SIZE));
            }
            if (isOccupied(occupied, tile.dx(), tile.dy() + 1)) {
                segments.add(
                    new Segment(
                        x,
                        y + StationMapFrame.TILE_SIZE,
                        StationMapFrame.TILE_SIZE,
                        StationMapFrame.CONNECTOR_GAP));
            }
            if (isOccupied(occupied, tile.dx() + 1, tile.dy()) && isOccupied(occupied, tile.dx(), tile.dy() + 1)
                && isOccupied(occupied, tile.dx() + 1, tile.dy() + 1)) {
                segments.add(
                    new Segment(
                        x + StationMapFrame.TILE_SIZE,
                        y + StationMapFrame.TILE_SIZE,
                        StationMapFrame.CONNECTOR_GAP,
                        StationMapFrame.CONNECTOR_GAP));
            }
        }
        return segments;
    }

    public static List<Segment> outlineSegments(ModuleShape shape, StationTileCoord anchor, int rotation,
        StationMapFrame frame) {
        return translated(geometry(shape, anchor, rotation).outline(), anchor, frame);
    }

    private static Segment boundsOf(List<Segment> filledSegments) {
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

        return new Segment(minX, minY, maxX - minX, maxY - minY);
    }

    private static List<Segment> buildOutlineSegments(List<Segment> filledSegments, Segment bounds) {
        int minX = bounds.x();
        int minY = bounds.y();
        boolean[][] filled = new boolean[bounds.height()][bounds.width()];
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
        StationMapFrame frame) {
        int relativeX = x - frame.tileLocalX(anchor);
        int relativeY = y - frame.tileLocalY(anchor);
        for (Segment segment : geometry(shape, anchor, rotation).filled()) {
            if (segment.contains(relativeX, relativeY)) return true;
        }
        return false;
    }

    private static boolean isOccupied(Set<StationTileCoord> occupied, int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return false;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return false;
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
