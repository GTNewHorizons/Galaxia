package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationMapWidget extends ParentWidget<StationMapWidget> {

    private final CelestialAsset.ID assetId;
    private final @Nullable Consumer<StationTileCoord> expansionSlotClickHandler;
    private final int contentLeft;
    private final int contentRightPadding;
    private final int contentVerticalPadding;

    private @Nullable StationTileCoord selected;
    private @Nullable StationTileCoord hovered;
    private final Set<StationTileCoord> expansionSlots = new LinkedHashSet<>();
    private @Nullable StationLayout cachedExpansionLayout;
    private long cachedExpansionLayoutVersion = -1L;

    private boolean listenersRegistered;

    public StationMapWidget(CelestialAsset.ID assetId) {
        this(assetId, null);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler) {
        this(assetId, expansionSlotClickHandler, 0, 0, 0);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        int contentLeft, int contentRightPadding, int contentVerticalPadding) {
        this.assetId = assetId;
        this.expansionSlotClickHandler = expansionSlotClickHandler;
        this.contentLeft = contentLeft;
        this.contentRightPadding = contentRightPadding;
        this.contentVerticalPadding = contentVerticalPadding;
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
            StationLayout layout = facility.stationLayout();
            if (layout == null) return false;
            boolean occupied = layout.isOccupied(hit);
            selected = hit;
            if (!occupied && expansionSlotClickHandler != null) expansionSlotClickHandler.accept(hit);
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
        updateExpansionSlots(layout);

        for (StationTileCoord slot : expansionSlots) {
            int sx = tileLocalX(slot);
            int sy = tileLocalY(slot);
            StationTileRenderer.drawEmptyExpansionSlot(context, sx, sy, StationTileRenderer.LOGICAL_TILE_SIZE);
        }

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            int tx = tileLocalX(coord);
            int ty = tileLocalY(coord);
            StationTileRenderer.drawOccupied(context, tx, ty, StationTileRenderer.LOGICAL_TILE_SIZE, e.getValue());
        }

        StationTileCoord hov = hovered;
        if (hov != null && (tiles.containsKey(hov) || expansionSlots.contains(hov))) {
            int hx = tileLocalX(hov);
            int hy = tileLocalY(hov);
            StationTileRenderer.drawHoverOverlay(hx, hy, StationTileRenderer.LOGICAL_TILE_SIZE);
        }

        StationTileCoord sel = selected;
        if (sel != null && (tiles.containsKey(sel) || expansionSlots.contains(sel))) {
            int sx = tileLocalX(sel);
            int sy = tileLocalY(sel);
            StationTileRenderer.drawSelectionOverlay(sx, sy, StationTileRenderer.LOGICAL_TILE_SIZE);
        }
    }

    private void updateHover(StationLayout layout) {
        int localX = toLocalMouseX(getContext().getMouseX());
        int localY = toLocalMouseY(getContext().getMouseY());
        hovered = hitTest(layout, localX, localY);
    }

    private void updateExpansionSlots(StationLayout layout) {
        long layoutVersion = layout.version();
        if (layout == cachedExpansionLayout && layoutVersion == cachedExpansionLayoutVersion) return;
        StationPlacementValidator.collectExpansionSlots(layout, expansionSlots);
        cachedExpansionLayout = layout;
        cachedExpansionLayoutVersion = layoutVersion;
    }

    private @Nullable AutomatedFacility resolveFacility() {
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility f ? f : null;
    }

    private @Nullable StationTileCoord hitTest(@Nullable StationLayout layout, int localX, int localY) {
        if (layout == null) return null;
        StationTileCoord coord = StationMapViewport.coordAt(
            localX,
            localY,
            getArea().width,
            getArea().height,
            StationTileRenderer.LOGICAL_TILE_SIZE,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding);
        if (coord == null) return null;
        if (layout.isOccupied(coord)) return coord;
        if (StationPlacementValidator.validate(layout, coord) == StationPlacementValidator.Result.OK) return coord;
        return null;
    }

    private int tileLocalX(StationTileCoord coord) {
        return StationMapViewport.tileLeftX(
            coord,
            getArea().width,
            StationTileRenderer.LOGICAL_TILE_SIZE,
            contentLeft,
            contentRightPadding);
    }

    private int tileLocalY(StationTileCoord coord) {
        return StationMapViewport.tileTopY(
            coord,
            getArea().height,
            StationTileRenderer.LOGICAL_TILE_SIZE,
            contentVerticalPadding);
    }

    private int toLocalMouseX(int mouseX) {
        return mouseX - getArea().rx;
    }

    private int toLocalMouseY(int mouseY) {
        return mouseY - getArea().ry;
    }
}
