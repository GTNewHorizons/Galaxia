package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationMapWidget extends ParentWidget<StationMapWidget> {

    private final CelestialAsset.ID assetId;

    private @Nullable StationTileCoord selected;
    private @Nullable StationTileCoord hovered;

    private boolean listenersRegistered;

    public StationMapWidget(CelestialAsset.ID assetId) {
        this.assetId = assetId;
    }

    public @Nullable StationTileCoord selection() {
        return selected;
    }

    @Override
    public void onInit() {
        super.onInit();
        if (listenersRegistered) return;
        listenersRegistered = true;
        listenGuiAction((IGuiAction.MousePressed) button -> {
            if (button != 0) return false;
            AutomatedFacility facility = resolveFacility();
            if (facility == null) return false;
            StationTileCoord hit = hitTest(
                facility.stationLayout(),
                toLocalMouseX(getContext().getMouseX()),
                toLocalMouseY(getContext().getMouseY()));
            if (hit == null) return false;
            selected = hit;
            return true;
        });
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawBackground(context, widgetTheme);
        AutomatedFacility facility = resolveFacility();
        if (facility == null) return;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return;

        updateHover(layout);

        Map<StationTileCoord, PlacedTile> tiles = layout.snapshot();
        Set<StationTileCoord> expansionSlots = computeExpansionSlots(tiles);

        int widgetX = getArea().x;
        int widgetY = getArea().y;
        int originX = widgetX + getArea().width / 2 - StationTileRenderer.LOGICAL_TILE_SIZE / 2;
        int originY = widgetY + getArea().height / 2 - StationTileRenderer.LOGICAL_TILE_SIZE / 2;

        for (StationTileCoord slot : expansionSlots) {
            int sx = originX + slot.dx() * StationTileRenderer.LOGICAL_TILE_SIZE;
            int sy = originY + slot.dy() * StationTileRenderer.LOGICAL_TILE_SIZE;
            StationTileRenderer.drawEmptyExpansionSlot(context, sx, sy, StationTileRenderer.LOGICAL_TILE_SIZE);
        }

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            int tx = originX + coord.dx() * StationTileRenderer.LOGICAL_TILE_SIZE;
            int ty = originY + coord.dy() * StationTileRenderer.LOGICAL_TILE_SIZE;
            StationTileRenderer.drawOccupied(context, tx, ty, StationTileRenderer.LOGICAL_TILE_SIZE, e.getValue());
        }

        StationTileCoord hov = hovered;
        if (hov != null && (tiles.containsKey(hov) || expansionSlots.contains(hov))) {
            int hx = originX + hov.dx() * StationTileRenderer.LOGICAL_TILE_SIZE;
            int hy = originY + hov.dy() * StationTileRenderer.LOGICAL_TILE_SIZE;
            StationTileRenderer.drawHoverOverlay(hx, hy, StationTileRenderer.LOGICAL_TILE_SIZE);
        }

        StationTileCoord sel = selected;
        if (sel != null && (tiles.containsKey(sel) || expansionSlots.contains(sel))) {
            int sx = originX + sel.dx() * StationTileRenderer.LOGICAL_TILE_SIZE;
            int sy = originY + sel.dy() * StationTileRenderer.LOGICAL_TILE_SIZE;
            StationTileRenderer.drawSelectionOverlay(sx, sy, StationTileRenderer.LOGICAL_TILE_SIZE);
        }
    }

    private void updateHover(StationLayout layout) {
        int localX = toLocalMouseX(getContext().getMouseX());
        int localY = toLocalMouseY(getContext().getMouseY());
        hovered = hitTest(layout, localX, localY);
    }

    private @Nullable AutomatedFacility resolveFacility() {
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility f ? f : null;
    }

    private @Nullable StationTileCoord hitTest(@Nullable StationLayout layout, int localX, int localY) {
        if (layout == null) return null;
        int size = StationTileRenderer.LOGICAL_TILE_SIZE;
        int originX = getArea().width / 2 - size / 2;
        int originY = getArea().height / 2 - size / 2;
        int relX = localX - originX;
        int relY = localY - originY;
        int dx = Math.floorDiv(relX, size);
        int dy = Math.floorDiv(relY, size);
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return null;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return null;
        StationTileCoord coord = StationTileCoord.of(dx, dy);
        if (layout.isOccupied(coord)) return coord;
        if (isExpansionSlot(layout.snapshot(), coord)) return coord;
        return null;
    }

    private int toLocalMouseX(int mouseX) {
        return mouseX - getArea().x;
    }

    private int toLocalMouseY(int mouseY) {
        return mouseY - getArea().y;
    }

    private static Set<StationTileCoord> computeExpansionSlots(Map<StationTileCoord, PlacedTile> tiles) {
        Set<StationTileCoord> slots = new HashSet<>();
        for (StationTileCoord occupied : tiles.keySet()) {
            addNeighbourIfExpansion(slots, tiles, occupied.dx() - 1, occupied.dy());
            addNeighbourIfExpansion(slots, tiles, occupied.dx() + 1, occupied.dy());
            addNeighbourIfExpansion(slots, tiles, occupied.dx(), occupied.dy() - 1);
            addNeighbourIfExpansion(slots, tiles, occupied.dx(), occupied.dy() + 1);
        }
        return slots;
    }

    private static void addNeighbourIfExpansion(Set<StationTileCoord> slots, Map<StationTileCoord, PlacedTile> tiles,
        int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return;
        StationTileCoord coord = StationTileCoord.of(dx, dy);
        if (!tiles.containsKey(coord)) slots.add(coord);
    }

    private static boolean isExpansionSlot(Map<StationTileCoord, PlacedTile> tiles, StationTileCoord coord) {
        return isOccupiedNeighbour(tiles, coord.dx() - 1, coord.dy())
            || isOccupiedNeighbour(tiles, coord.dx() + 1, coord.dy())
            || isOccupiedNeighbour(tiles, coord.dx(), coord.dy() - 1)
            || isOccupiedNeighbour(tiles, coord.dx(), coord.dy() + 1);
    }

    private static boolean isOccupiedNeighbour(Map<StationTileCoord, PlacedTile> tiles, int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return false;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return false;
        return tiles.containsKey(StationTileCoord.of(dx, dy));
    }
}
