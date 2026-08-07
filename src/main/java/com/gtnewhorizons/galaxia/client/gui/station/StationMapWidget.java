package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.station.layer.CapacityConnectorLayer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ConnectionLayerRenderer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ModuleLayerRenderer;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationPlacementValidator;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationMapWidget extends ParentWidget<StationMapWidget> implements Interactable {

    private final CelestialAsset.ID assetId;
    private final @Nullable Consumer<StationTileCoord> expansionSlotClickHandler;
    private final @Nullable Consumer<PlacedTile> moduleSelectionHandler;
    private final int contentLeft;
    private final int contentRightPadding;
    private final int contentVerticalPadding;
    private final StationVisionLayer visionLayer;
    private final BiPredicate<Integer, Integer> inputBlocked;
    private final @Nullable StationTilePickerController tilePickerController;
    private final StationFeatureSurface featureSurface = new StationFeatureSurface();

    private @Nullable StationTileCoord selected;
    private @Nullable StationTileCoord hovered;
    private @Nullable StationTileCoord pressedTile;
    private final List<StationMapViewport.TilePosition> visibleFeatureTiles = new ArrayList<>();
    private final List<PlanetaryFeatureDefinition> hoveredFeatureDefinitions = new ArrayList<>();
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
        this(
            assetId,
            expansionSlotClickHandler,
            moduleSelectionHandler,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            visionLayer,
            inputBlocked,
            null);
    }

    public StationMapWidget(CelestialAsset.ID assetId, @Nullable Consumer<StationTileCoord> expansionSlotClickHandler,
        @Nullable Consumer<PlacedTile> moduleSelectionHandler, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, StationVisionLayer visionLayer, BiPredicate<Integer, Integer> inputBlocked,
        @Nullable StationTilePickerController tilePickerController) {
        this.assetId = assetId;
        this.expansionSlotClickHandler = expansionSlotClickHandler;
        this.moduleSelectionHandler = moduleSelectionHandler;
        this.contentLeft = contentLeft;
        this.contentRightPadding = contentRightPadding;
        this.contentVerticalPadding = contentVerticalPadding;
        this.visionLayer = visionLayer;
        this.inputBlocked = inputBlocked;
        this.tilePickerController = tilePickerController;
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
            if (isPickerActive()) {
                tilePickerController.toggleNormalized(normalizePickerTarget(hit));
                pressedTile = null;
                return true;
            }
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
    public Result onKeyPressed(char typedChar, int keyCode) {
        if (isPickerActive() && keyCode == Keyboard.KEY_R && tilePickerController.rotateSelectionFootprint()) {
            return Result.SUCCESS;
        }
        return Result.IGNORE;
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
        Map<ModuleInstance.ID, List<StationModuleAlert>> moduleAlerts = StationModuleAlertRegistry.alerts(facility);
        updateExpansionSlots(layout);

        StationMapFrame frame = mapFrame();

        StationMapOverlayPainter.drawFeatureOverlay(facility, frame, visibleFeatureTiles);

        ConnectionLayerRenderer.draw(context, tiles, frame);

        CapacityConnectorLayer.draw(context, tiles, frame);

        for (StationTileCoord slot : expansionSlots) {
            int sx = tileLocalX(slot);
            int sy = tileLocalY(slot);
            ModuleLayerRenderer.drawExpansionSlot(context, sx, sy);
        }

        ModuleLayerRenderer.drawFootprintTextures(tiles, frame);

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            int tx = tileLocalX(coord);
            int ty = tileLocalY(coord);
            ModuleLayerRenderer.drawOccupied(context, tx, ty, coord, e.getValue());
        }
        StationMapOverlayPainter.drawModuleAlerts(tiles, moduleAlerts, frame);

        drawPickerOverlay(context, tiles, frame);

        StationMapOverlayPainter.drawCoreDirectionIndicator(tiles.keySet(), frame);

        StationTileCoord hov = hovered;
        if (hov != null && (tiles.containsKey(hov) || expansionSlots.contains(hov))) {
            StationMapOverlayPainter.drawHoverOverlay(hov, tiles, frame);
        }

        StationTileCoord sel = selected;
        if (!isPickerActive() && sel != null && (tiles.containsKey(sel) || expansionSlots.contains(sel))) {
            StationMapOverlayPainter.drawSelectionOverlay(sel, tiles, frame);
        }

        StationMapOverlayPainter.drawMaintenanceBayCoverage(sel, tiles, frame);

        int localMouseX = toLocalMouseX(getContext().getMouseX());
        int localMouseY = toLocalMouseY(getContext().getMouseY());
        StationMapOverlayPainter
            .drawFeatureTooltip(facility, featureSurface, localMouseX, localMouseY, frame, hoveredFeatureDefinitions);
        StationMapOverlayPainter.drawModuleAlertTooltip(tiles, moduleAlerts, hovered, localMouseX, localMouseY, frame);
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

    private void drawPickerOverlay(ModularGuiContext context, Map<StationTileCoord, PlacedTile> tiles,
        StationMapFrame frame) {
        if (!isPickerActive()) return;
        if (tilePickerController.visualStyle() == StationTilePickerController.VisualStyle.DECONSTRUCT) {
            drawDeconstructPickerOverlay(tiles, frame);
            return;
        }
        ModuleShape footprint = tilePickerController.selectionFootprint();
        Set<StationTileCoord> touchTiles = new LinkedHashSet<>(expansionSlots);
        Set<StationTileCoord> candidateAnchors = new LinkedHashSet<>();
        Set<StationTileCoord> clickableTiles = new LinkedHashSet<>();
        for (StationTileCoord selectedTarget : tilePickerController.selectedTargets()) {
            int selectedRotation = tilePickerController.selectedTargetRotation(selectedTarget);
            addFootprintOrthogonalCandidates(touchTiles, selectedTarget, footprint, selectedRotation);
            drawPickerFootprint(
                selectedTarget,
                footprint,
                selectedRotation,
                true,
                pickerPrimaryTile(selectedTarget, footprint, selectedRotation));
        }
        addFootprintAnchorsContaining(
            candidateAnchors,
            touchTiles,
            footprint,
            tilePickerController.footprintRotation());
        for (StationTileCoord anchor : candidateAnchors) {
            if (!tilePickerController.isCompatibleNormalized(anchor) || tilePickerController.isSelected(anchor))
                continue;
            StationTileCoord clickTile = ModuleBuildPickerModel
                .tileForAnchorRotation(anchor, footprint, tilePickerController.footprintRotation());
            if (clickTile == null || !clickableTiles.add(clickTile)) continue;
            int x = tileLocalX(clickTile);
            int y = tileLocalY(clickTile);
            StationTileRenderer.drawPickerCompatibleOverlay(x, y, StationMapViewport.TILE_SIZE);
        }
        drawPickerHoverFootprint(context, footprint, frame);
    }

    private void drawDeconstructPickerOverlay(Map<StationTileCoord, PlacedTile> tiles, StationMapFrame frame) {
        Set<ModuleInstance.ID> drawnModules = new LinkedHashSet<>();
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            StationTileCoord coord = entry.getKey();
            ModuleInstance module = entry.getValue() == null ? null
                : entry.getValue()
                    .module();
            if (drawDeconstructModulePickerOverlay(module, drawnModules, frame)) continue;
            drawDeconstructTilePickerOverlay(coord);
        }
    }

    private boolean drawDeconstructModulePickerOverlay(ModuleInstance module, Set<ModuleInstance.ID> drawnModules,
        StationMapFrame frame) {
        if (module == null || module.shape()
            .tileCount() == 1) return false;
        if (!drawnModules.add(module.id)) return true;
        StationTileCoord normalized = normalizePickerTarget(module.anchor());
        if (!tilePickerController.isCompatibleNormalized(normalized)) return true;
        StationMapOverlayPainter
            .drawDeconstructModuleOverlay(module, tilePickerController.isSelected(normalized), frame);
        return true;
    }

    private void drawDeconstructTilePickerOverlay(StationTileCoord coord) {
        StationTileCoord normalized = normalizePickerTarget(coord);
        if (!tilePickerController.isCompatibleNormalized(normalized)) return;
        int x = tileLocalX(coord);
        int y = tileLocalY(coord);
        if (tilePickerController.isSelected(coord)) {
            StationTileRenderer.drawPickerDeconstructSelectedOverlay(x, y, StationMapViewport.TILE_SIZE);
        } else {
            StationTileRenderer.drawPickerCompatibleOverlay(x, y, StationMapViewport.TILE_SIZE);
        }
    }

    private void drawPickerHoverFootprint(ModularGuiContext context, ModuleShape footprint, StationMapFrame frame) {
        StationTileCoord hov = hovered;
        if (hov == null) return;
        StationTileCoord normalized = normalizePickerTarget(hov);
        if (!tilePickerController.isCompatibleNormalized(normalized)) return;
        int rotation = tilePickerController.isSelected(normalized)
            ? tilePickerController.selectedTargetRotation(normalized)
            : tilePickerController.footprintRotation();
        drawPickerModulePreview(
            context,
            normalized,
            footprint,
            rotation,
            hov,
            tilePickerController.isSelected(normalized),
            frame);
    }

    private void drawPickerModulePreview(ModularGuiContext context, StationTileCoord anchor, ModuleShape footprint,
        int rotation, StationTileCoord primaryTile, boolean selected, StationMapFrame frame) {
        FacilityModuleKind kind = tilePickerController.previewModuleKind();
        if (kind == null || anchor == null || footprint == null) return;
        boolean drewFootprintTexture = ModuleLayerRenderer
            .drawPreviewFootprint(context, kind, footprint, anchor, rotation, frame);
        for (StationTileCoord tile : footprint.tiles(anchor, rotation)) {
            int x = tileLocalX(tile);
            int y = tileLocalY(tile);
            if (!drewFootprintTexture) {
                ModuleLayerRenderer.drawPreview(context, x, y, kind);
            }
            drawPickerTileOutline(x, y, selected, tile.equals(primaryTile));
        }
    }

    private void drawPickerFootprint(StationTileCoord anchor, ModuleShape footprint, boolean selected) {
        int rotation = tilePickerController.footprintRotation();
        drawPickerFootprint(anchor, footprint, rotation, selected, pickerPrimaryTile(anchor, footprint, rotation));
    }

    private StationTileCoord pickerPrimaryTile(StationTileCoord anchor, ModuleShape footprint, int rotation) {
        return ModuleBuildPickerModel.tileForAnchorRotation(anchor, footprint, rotation);
    }

    private void drawPickerFootprint(StationTileCoord anchor, ModuleShape footprint, int rotation, boolean selected,
        @Nullable StationTileCoord primaryTile) {
        if (anchor == null || footprint == null) return;
        for (StationTileCoord tile : footprint.tiles(anchor, rotation)) {
            int x = tileLocalX(tile);
            int y = tileLocalY(tile);
            drawPickerTileOutline(x, y, selected, tile.equals(primaryTile));
        }
    }

    private static void drawPickerTileOutline(int x, int y, boolean selected, boolean primary) {
        if (selected) {
            if (primary) {
                StationTileRenderer.drawPickerSelectedOverlay(x, y, StationMapViewport.TILE_SIZE);
            } else {
                StationTileRenderer.drawPickerSelectedSecondaryOverlay(x, y, StationMapViewport.TILE_SIZE);
            }
        } else {
            if (primary) {
                StationTileRenderer.drawPickerCompatibleOverlay(x, y, StationMapViewport.TILE_SIZE);
            } else {
                StationTileRenderer.drawPickerCompatibleSecondaryOverlay(x, y, StationMapViewport.TILE_SIZE);
            }
        }
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
        if (!isPickerActive()) {
            StationTileCoord moduleHit = StationMapHitTester.hitTestModuleFootprint(layout, localX, localY, mapFrame());
            if (moduleHit != null) return moduleHit;
        }
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
        if (isPickerActive()) {
            StationTileCoord normalized = normalizePickerTarget(coord);
            return tilePickerController.isCompatibleNormalized(normalized) ? coord : null;
        }
        if (layout.isOccupied(coord)) return coord;
        if (StationPlacementValidator.validate(layout, coord) == StationPlacementValidator.Result.OK) return coord;
        return null;
    }

    private StationMapFrame mapFrame() {
        return new StationMapFrame(
            getArea().width,
            getArea().height,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
    }

    private StationTileCoord normalizePickerTarget(StationTileCoord coord) {
        if (!isPickerActive() || coord == null) return coord;
        StationTileCoord anchor = coord;
        if (tilePickerController.rotatesFootprint()) {
            anchor = ModuleBuildPickerModel.anchorForRotation(
                coord,
                tilePickerController.selectionFootprint(),
                tilePickerController.footprintRotation());
        }
        return tilePickerController.normalize(anchor);
    }

    private static void addOrthogonalCandidates(Set<StationTileCoord> candidates, StationTileCoord coord) {
        addCandidate(candidates, coord.dx() - 1, coord.dy());
        addCandidate(candidates, coord.dx() + 1, coord.dy());
        addCandidate(candidates, coord.dx(), coord.dy() - 1);
        addCandidate(candidates, coord.dx(), coord.dy() + 1);
    }

    private static void addFootprintOrthogonalCandidates(Set<StationTileCoord> candidates, StationTileCoord anchor,
        ModuleShape footprint, int rotation) {
        if (footprint == null) return;
        for (StationTileCoord tile : footprint.tiles(anchor, rotation)) {
            addOrthogonalCandidates(candidates, tile);
        }
    }

    private static void addFootprintAnchorsContaining(Set<StationTileCoord> anchors, Set<StationTileCoord> tiles,
        ModuleShape footprint, int rotation) {
        if (footprint == null || tiles == null) return;
        for (StationTileCoord tile : tiles) {
            addFootprintAnchorsContaining(anchors, tile, footprint, rotation);
        }
    }

    private static void addFootprintAnchorsContaining(Set<StationTileCoord> anchors, StationTileCoord tile,
        ModuleShape footprint, int rotation) {
        if (tile == null) return;
        if (footprint == ModuleShape.SINGLE) {
            anchors.add(tile);
            return;
        }
        for (StationTileCoord offset : footprint.tiles(StationTileCoord.CORE, rotation)) {
            int anchorDx = tile.dx() - offset.dx();
            int anchorDy = tile.dy() - offset.dy();
            if (anchorDx < StationTileCoord.MIN || anchorDx > StationTileCoord.MAX) continue;
            if (anchorDy < StationTileCoord.MIN || anchorDy > StationTileCoord.MAX) continue;
            StationTileCoord anchor = StationTileCoord.of(anchorDx, anchorDy);
            if (footprint.fitsAt(anchor, rotation)) anchors.add(anchor);
        }
    }

    private static void addCandidate(Set<StationTileCoord> candidates, int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return;
        candidates.add(StationTileCoord.of(dx, dy));
    }

    private int tileLocalX(StationTileCoord coord) {
        return tileLocalX(coord.dx());
    }

    private int tileLocalY(StationTileCoord coord) {
        return tileLocalY(coord.dy());
    }

    private int tileLocalX(int dx) {
        return StationMapViewport.tileLeftX(dx, getArea().width, contentLeft, contentRightPadding, panX);
    }

    private int tileLocalY(int dy) {
        return StationMapViewport.tileTopY(dy, getArea().height, contentVerticalPadding, panY);
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

    private boolean isPickerActive() {
        return tilePickerController != null && tilePickerController.isActive();
    }

    private void clearPressState() {
        pressInMapContent = false;
        dragging = false;
        pressedTile = null;
    }
}
