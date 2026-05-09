package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.layer.CapacityConnectorLayer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ConnectionLayerRenderer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ModuleLayerRenderer;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationMapWidget extends ParentWidget<StationMapWidget> {

    private final CelestialAsset.ID assetId;
    private final @Nullable Consumer<StationTileCoord> expansionSlotClickHandler;
    private final @Nullable Consumer<PlacedTile> moduleSelectionHandler;
    private final int contentLeft;
    private final int contentRightPadding;
    private final int contentVerticalPadding;
    private final StationVisionLayer visionLayer;
    private final BiPredicate<Integer, Integer> inputBlocked;

    private @Nullable StationTileCoord selected;
    private @Nullable StationTileCoord hovered;
    private @Nullable StationTileCoord pressedTile;
    private final Set<StationTileCoord> expansionSlots = new LinkedHashSet<>();
    private @Nullable StationLayout cachedExpansionLayout;
    private long cachedExpansionLayoutVersion = -1L;
    private int panX;
    private int panY;
    private int pressMouseX;
    private int pressMouseY;
    private int lastDragMouseX;
    private int lastDragMouseY;
    private boolean pressInMapContent;
    private boolean dragging;

    private boolean listenersRegistered;
    private static final int CLICK_DRAG_THRESHOLD = 3;

    public StationMapWidget(CelestialAsset.ID assetId) {
        this(assetId, null, null);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler) {
        this(assetId, expansionSlotClickHandler, null);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        @Nullable Consumer<PlacedTile> moduleSelectionHandler) {
        this(assetId, expansionSlotClickHandler, moduleSelectionHandler, 0, 0, 0);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        int contentLeft, int contentRightPadding, int contentVerticalPadding) {
        this(assetId, expansionSlotClickHandler, null, contentLeft, contentRightPadding, contentVerticalPadding);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        @Nullable Consumer<PlacedTile> moduleSelectionHandler, int contentLeft, int contentRightPadding,
        int contentVerticalPadding) {
        this(
            assetId,
            expansionSlotClickHandler,
            moduleSelectionHandler,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            StationVisionLayer.BASE);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, StationVisionLayer visionLayer) {
        this(
            assetId,
            expansionSlotClickHandler,
            null,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            visionLayer);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        @Nullable Consumer<PlacedTile> moduleSelectionHandler, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, StationVisionLayer visionLayer) {
        this(
            assetId,
            expansionSlotClickHandler,
            moduleSelectionHandler,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            visionLayer,
            (mouseX, mouseY) -> false);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        @Nullable Consumer<PlacedTile> moduleSelectionHandler, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, StationVisionLayer visionLayer, BiPredicate<Integer, Integer> inputBlocked) {
        this.assetId = assetId;
        this.expansionSlotClickHandler = expansionSlotClickHandler;
        this.moduleSelectionHandler = moduleSelectionHandler;
        this.contentLeft = contentLeft;
        this.contentRightPadding = contentRightPadding;
        this.contentVerticalPadding = contentVerticalPadding;
        this.visionLayer = visionLayer;
        this.inputBlocked = inputBlocked;
    }

    public @Nullable CelestialAsset.ID assetId() {
        return assetId;
    }

    public @Nullable StationTileCoord selection() {
        return selected;
    }

    public StationVisionLayer visionLayer() {
        return visionLayer;
    }

    @Override
    public void onInit() {
        super.onInit();
        if (listenersRegistered) return;
        listenersRegistered = true;
        listenGuiAction((IGuiAction.MousePressed) button -> {
            if (button != 0) return false;
            if (isInputBlocked()) {
                clearPressState();
                return false;
            }
            AutomatedFacility facility = resolveFacility();
            if (facility == null) return false;
            pressMouseX = toLocalMouseX(getContext().getMouseX());
            pressMouseY = toLocalMouseY(getContext().getMouseY());
            pressInMapContent = StationMapViewport.contains(
                pressMouseX,
                pressMouseY,
                getArea().width,
                getArea().height,
                contentLeft,
                contentRightPadding,
                contentVerticalPadding);
            if (!pressInMapContent) return false;
            pressedTile = hitTest(facility.stationLayout(), pressMouseX, pressMouseY);
            lastDragMouseX = pressMouseX;
            lastDragMouseY = pressMouseY;
            dragging = false;
            return true;
        });
        listenGuiAction((IGuiAction.MouseDrag) (mouseButton, time) -> {
            if (isInputBlocked()) {
                clearPressState();
                return false;
            }
            if (mouseButton != 0 || !pressInMapContent) return false;
            updateManualDragging();
            return true;
        });
        listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
            if (isInputBlocked()) {
                clearPressState();
                return false;
            }
            if (mouseButton != 0 || !pressInMapContent) return false;
            boolean wasDragging = dragging;
            pressInMapContent = false;
            dragging = false;
            if (wasDragging) {
                pressedTile = null;
                return true;
            }
            AutomatedFacility facility = resolveFacility();
            if (facility == null) return false;
            StationLayout layout = facility.stationLayout();
            if (layout == null) return false;
            StationTileCoord hit = hitTest(
                layout,
                toLocalMouseX(getContext().getMouseX()),
                toLocalMouseY(getContext().getMouseY()));
            if (hit == null || !hit.equals(pressedTile)) return false;
            boolean occupied = layout.isOccupied(hit);
            selected = hit;
            if (occupied && moduleSelectionHandler != null) {
                PlacedTile tile = layout.get(hit);
                if (tile != null) moduleSelectionHandler.accept(tile);
            }
            if (!occupied && expansionSlotClickHandler != null) expansionSlotClickHandler.accept(hit);
            pressedTile = null;
            return true;
        });
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawBackground(context, widgetTheme);
        updateManualDragging();
        AutomatedFacility facility = resolveFacility();
        if (facility == null) return;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return;

        updateHover(layout);
        Map<StationTileCoord, PlacedTile> tiles = layout.snapshot();
        updateExpansionSlots(layout);

        int widgetWidth = getArea().width;
        int widgetHeight = getArea().height;

        ConnectionLayerRenderer.draw(
            context,
            tiles,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);

        CapacityConnectorLayer.draw(
            context,
            tiles,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);

        for (StationTileCoord slot : expansionSlots) {
            int sx = tileLocalX(slot);
            int sy = tileLocalY(slot);
            ModuleLayerRenderer.drawExpansionSlot(context, sx, sy);
        }

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            int tx = tileLocalX(coord);
            int ty = tileLocalY(coord);
            ModuleLayerRenderer.drawOccupied(context, tx, ty, e.getValue());
        }

        StationTileCoord hov = hovered;
        if (hov != null && (tiles.containsKey(hov) || expansionSlots.contains(hov))) {
            int hx = tileLocalX(hov);
            int hy = tileLocalY(hov);
            StationTileRenderer.drawHoverOverlay(hx, hy, StationMapViewport.TILE_SIZE);
        }

        StationTileCoord sel = selected;
        if (sel != null && (tiles.containsKey(sel) || expansionSlots.contains(sel))) {
            int sx = tileLocalX(sel);
            int sy = tileLocalY(sel);
            StationTileRenderer.drawSelectionOverlay(sx, sy, StationMapViewport.TILE_SIZE);
        }

        // T3.9: Maintenance Bay coverage overlay — highlight 8 affected tiles when a bay is selected
        if (sel != null && tiles.containsKey(sel)) {
            PlacedTile selTile = tiles.get(sel);
            ModuleInstance selModule = selTile != null ? selTile.module() : null;
            if (selModule != null && selModule.kind() == FacilityModuleKind.MAINTENANCE_BAY) {
                StationTileCoord anchor = selModule.anchor();
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = anchor.dx() + dx;
                        int ny = anchor.dy() + dy;
                        if (nx < StationTileCoord.MIN || nx > StationTileCoord.MAX
                            || ny < StationTileCoord.MIN
                            || ny > StationTileCoord.MAX) continue;
                        StationTileCoord ncoord = StationTileCoord.of(nx, ny);
                        int hx = tileLocalX(ncoord);
                        int hy = tileLocalY(ncoord);
                        BorderedRect.draw(
                            hx,
                            hy,
                            StationMapViewport.TILE_SIZE,
                            StationMapViewport.TILE_SIZE,
                            0x4400FF00, // semi-transparent green fill
                            0xFF00FF00); // solid green border
                    }
                }
            }
        }
    }

    private void updateHover(StationLayout layout) {
        if (isInputBlocked()) {
            hovered = null;
            return;
        }
        int localX = toLocalMouseX(getContext().getMouseX());
        int localY = toLocalMouseY(getContext().getMouseY());
        hovered = hitTest(layout, localX, localY);
    }

    private void updateManualDragging() {
        if (!pressInMapContent || !Mouse.isButtonDown(0)) return;
        int localX = toLocalMouseX(getContext().getMouseX());
        int localY = toLocalMouseY(getContext().getMouseY());
        if (!dragging) {
            if (Math.abs(localX - pressMouseX) <= CLICK_DRAG_THRESHOLD
                && Math.abs(localY - pressMouseY) <= CLICK_DRAG_THRESHOLD) return;
            dragging = true;
            lastDragMouseX = localX;
            lastDragMouseY = localY;
            return;
        }
        int dx = localX - lastDragMouseX;
        int dy = localY - lastDragMouseY;
        if (dx == 0 && dy == 0) return;
        panX += dx;
        panY += dy;
        lastDragMouseX = localX;
        lastDragMouseY = localY;
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
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
        if (coord == null) return null;
        if (layout.isOccupied(coord)) return coord;
        if (StationPlacementValidator.validate(layout, coord) == StationPlacementValidator.Result.OK) return coord;
        return null;
    }

    private int tileLocalX(StationTileCoord coord) {
        return StationMapViewport.tileLeftX(coord, getArea().width, contentLeft, contentRightPadding, panX);
    }

    private int tileLocalY(StationTileCoord coord) {
        return StationMapViewport.tileTopY(coord, getArea().height, contentVerticalPadding, panY);
    }

    private int toLocalMouseX(int mouseX) {
        return mouseX - getArea().rx;
    }

    private int toLocalMouseY(int mouseY) {
        return mouseY - getArea().ry;
    }

    private boolean isInputBlocked() {
        return inputBlocked.test(getContext().getMouseX(), getContext().getMouseY());
    }

    private void clearPressState() {
        pressInMapContent = false;
        dragging = false;
        pressedTile = null;
    }
}
