package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.layer.CapacityConnectorLayer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ConnectionLayerRenderer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ModuleLayerRenderer;
import com.gtnewhorizons.galaxia.client.gui.station.layer.PlanetaryFeatureOverlayRenderer;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
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
    private static final int ALERT_ICON_SIZE = 8;
    private static final ResourceLocation DEFAULT_ALERT_ICON = EnumTextures.ICON_STATION_ALERT_WARNING.get();
    private static final ResourceLocation DEFAULT_RED_ALERT_ICON = EnumTextures.ICON_STATION_ALERT_ERROR.get();

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

        drawFeatureOverlay(facility);

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
        drawModuleAlerts(tiles, moduleAlerts);

        drawPickerOverlay(context, tiles);

        drawCoreDirectionIndicator(tiles.keySet());

        StationTileCoord hov = hovered;
        if (hov != null && (tiles.containsKey(hov) || expansionSlots.contains(hov))) {
            int hx = tileLocalX(hov);
            int hy = tileLocalY(hov);
            StationTileRenderer.drawHoverOverlay(hx, hy, StationMapViewport.TILE_SIZE);
        }

        StationTileCoord sel = selected;
        if (!isPickerActive() && sel != null && (tiles.containsKey(sel) || expansionSlots.contains(sel))) {
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
                            EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_FILL.getColor(),
                            EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_BORDER.getColor());
                    }
                }
            }
        }

        drawFeatureTooltip(facility);
        drawModuleAlertTooltip(tiles, moduleAlerts);
    }

    private void drawModuleAlerts(Map<StationTileCoord, PlacedTile> tiles,
        Map<ModuleInstance.ID, List<StationModuleAlert>> moduleAlerts) {
        if (moduleAlerts.isEmpty()) return;
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            ModuleInstance module = moduleOf(entry.getValue());
            if (module == null || !entry.getKey()
                .equals(alertBadgeCoord(module, tiles))) {
                continue;
            }
            StationModuleAlert alert = firstAlert(moduleAlerts, module);
            if (alert == null) continue;
            drawModuleAlertIcon(tileLocalX(entry.getKey()), tileLocalY(entry.getKey()), alert);
        }
    }

    private static void drawModuleAlertIcon(int tileX, int tileY, StationModuleAlert alert) {
        ResourceLocation icon = alert.icon() != null ? alert.icon() : defaultAlertIcon(alert.severity());
        ModuleConfigModalSupport.renderTextureIcon(icon, tileX + 2, tileY + 2, ALERT_ICON_SIZE, ALERT_ICON_SIZE);
    }

    private void drawModuleAlertTooltip(Map<StationTileCoord, PlacedTile> tiles,
        Map<ModuleInstance.ID, List<StationModuleAlert>> moduleAlerts) {
        if (moduleAlerts.isEmpty() || hovered == null) return;
        PlacedTile tile = tiles.get(hovered);
        ModuleInstance module = moduleOf(tile);
        if (module == null) return;
        List<StationModuleAlert> alerts = moduleAlerts.get(module.id);
        if (alerts == null || alerts.isEmpty()) return;

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int localX = toLocalMouseX(getContext().getMouseX());
        int localY = toLocalMouseY(getContext().getMouseY());
        int maxTextWidth = Math.max(40, Math.min(180, getArea().width - 20));
        int tooltipWidth = 40;
        for (StationModuleAlert alert : alerts) {
            tooltipWidth = Math.max(tooltipWidth, fr.getStringWidth(fr.trimStringToWidth(alert.title(), maxTextWidth)));
            tooltipWidth = Math
                .max(tooltipWidth, fr.getStringWidth(fr.trimStringToWidth(alert.message(), maxTextWidth)));
        }
        tooltipWidth += 12;
        int tooltipHeight = 8 + alerts.size() * (fr.FONT_HEIGHT * 2 + 6);
        int tooltipX = Math.min(localX + 10, getArea().width - tooltipWidth - 2);
        int tooltipY = Math.min(localY + 10, getArea().height - tooltipHeight - 2);
        tooltipX = Math.max(2, tooltipX);
        tooltipY = Math.max(2, tooltipY);
        boolean red = hasRedAlert(alerts);
        BorderedRect.draw(
            tooltipX,
            tooltipY,
            tooltipWidth,
            tooltipHeight,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            red ? EnumColors.MAP_COLOR_RECIPE_BOUND_MARKER_BLOCKING.getColor()
                : EnumColors.MAP_COLOR_RECIPE_BOUND_MARKER_WARNING.getColor());
        int textY = tooltipY + 4;
        for (StationModuleAlert alert : alerts) {
            String title = fr.trimStringToWidth(alert.title(), maxTextWidth);
            String message = fr.trimStringToWidth(alert.message(), maxTextWidth);
            fr.drawStringWithShadow(title, tooltipX + 6, textY, alertTitleColor(alert));
            textY += fr.FONT_HEIGHT + 2;
            fr.drawStringWithShadow(message, tooltipX + 6, textY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            textY += fr.FONT_HEIGHT + 4;
        }
    }

    private static @Nullable StationModuleAlert firstAlert(
        Map<ModuleInstance.ID, List<StationModuleAlert>> moduleAlerts, ModuleInstance module) {
        List<StationModuleAlert> alerts = moduleAlerts.get(module.id);
        return alerts == null || alerts.isEmpty() ? null : alerts.get(0);
    }

    private static @Nullable ModuleInstance moduleOf(@Nullable PlacedTile tile) {
        return tile == null ? null : tile.module();
    }

    private static ResourceLocation defaultAlertIcon(StationModuleAlert.Severity severity) {
        return severity == StationModuleAlert.Severity.RED ? DEFAULT_RED_ALERT_ICON : DEFAULT_ALERT_ICON;
    }

    private static boolean hasRedAlert(List<StationModuleAlert> alerts) {
        for (StationModuleAlert alert : alerts) {
            if (alert.severity() == StationModuleAlert.Severity.RED) return true;
        }
        return false;
    }

    private static int alertTitleColor(StationModuleAlert alert) {
        return alert.severity() == StationModuleAlert.Severity.RED ? EnumColors.MAP_COLOR_TEXT_DANGER.getColor()
            : EnumColors.MAP_COLOR_RECIPE_BOUND_MARKER_WARNING.getColor();
    }

    private static StationTileCoord alertBadgeCoord(ModuleInstance module, Map<StationTileCoord, PlacedTile> tiles) {
        int minDx = module.anchor()
            .dx();
        int minDy = module.anchor()
            .dy();
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            ModuleInstance tileModule = moduleOf(entry.getValue());
            if (tileModule == null || !module.id.equals(tileModule.id)) continue;
            minDx = Math.min(
                minDx,
                entry.getKey()
                    .dx());
            minDy = Math.min(
                minDy,
                entry.getKey()
                    .dy());
        }
        return StationTileCoord.of(minDx, minDy);
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

    private void drawPickerOverlay(ModularGuiContext context, Map<StationTileCoord, PlacedTile> tiles) {
        if (!isPickerActive()) return;
        if (tilePickerController.visualStyle() == StationTilePickerController.VisualStyle.DECONSTRUCT) {
            drawDeconstructPickerOverlay(tiles);
            return;
        }
        ModuleShape footprint = tilePickerController.selectionFootprint();
        Set<StationTileCoord> touchTiles = new LinkedHashSet<>(expansionSlots);
        Set<StationTileCoord> candidateAnchors = new LinkedHashSet<>();
        Set<StationTileCoord> clickableTiles = new LinkedHashSet<>();
        for (StationTileCoord selectedTarget : tilePickerController.selectedTargets()) {
            addFootprintOrthogonalCandidates(touchTiles, selectedTarget, footprint);
            drawPickerFootprint(selectedTarget, footprint, true, pickerPrimaryTile(selectedTarget, footprint));
        }
        addFootprintAnchorsContaining(candidateAnchors, touchTiles, footprint);
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
        drawPickerHoverFootprint(context, footprint);
    }

    private void drawDeconstructPickerOverlay(Map<StationTileCoord, PlacedTile> tiles) {
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            StationTileCoord coord = entry.getKey();
            StationTileCoord normalized = normalizePickerTarget(coord);
            if (!tilePickerController.isCompatibleNormalized(normalized)) continue;
            int x = tileLocalX(coord);
            int y = tileLocalY(coord);
            if (tilePickerController.isSelected(coord)) {
                StationTileRenderer.drawPickerDeconstructSelectedOverlay(x, y, StationMapViewport.TILE_SIZE);
            } else {
                StationTileRenderer.drawPickerCompatibleOverlay(x, y, StationMapViewport.TILE_SIZE);
            }
        }
    }

    private void drawPickerHoverFootprint(ModularGuiContext context, ModuleShape footprint) {
        StationTileCoord hov = hovered;
        if (hov == null) return;
        StationTileCoord normalized = normalizePickerTarget(hov);
        if (!tilePickerController.isCompatibleNormalized(normalized)) return;
        drawPickerModulePreview(context, normalized, footprint, hov, tilePickerController.isSelected(normalized));
    }

    private void drawPickerModulePreview(ModularGuiContext context, StationTileCoord anchor, ModuleShape footprint,
        StationTileCoord primaryTile, boolean selected) {
        FacilityModuleKind kind = tilePickerController.previewModuleKind();
        if (kind == null || anchor == null || footprint == null) return;
        for (StationTileCoord tile : footprint.tiles(anchor)) {
            int x = tileLocalX(tile);
            int y = tileLocalY(tile);
            ModuleLayerRenderer.drawPreview(context, x, y, kind);
            drawPickerTileOutline(x, y, selected, tile.equals(primaryTile));
        }
    }

    private void drawPickerFootprint(StationTileCoord anchor, ModuleShape footprint, boolean selected) {
        drawPickerFootprint(anchor, footprint, selected, pickerPrimaryTile(anchor, footprint));
    }

    private StationTileCoord pickerPrimaryTile(StationTileCoord anchor, ModuleShape footprint) {
        return ModuleBuildPickerModel
            .tileForAnchorRotation(anchor, footprint, tilePickerController.footprintRotation());
    }

    private void drawPickerFootprint(StationTileCoord anchor, ModuleShape footprint, boolean selected,
        @Nullable StationTileCoord primaryTile) {
        if (anchor == null || footprint == null) return;
        for (StationTileCoord tile : footprint.tiles(anchor)) {
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

    private void drawFeatureOverlay(AutomatedFacility facility) {
        StationMapViewport.collectVisibleTilePositions(
            getArea().width,
            getArea().height,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY,
            visibleFeatureTiles);
        for (StationMapViewport.TilePosition coord : visibleFeatureTiles) {
            PlanetaryFeatureOverlayRenderer.draw(
                tileLocalX(coord.dx()),
                tileLocalY(coord.dy()),
                facility.planetaryFeaturesAt(coord.dx(), coord.dy()));
        }
    }

    private void drawCoreDirectionIndicator(Set<StationTileCoord> occupiedTiles) {
        if (hasVisibleStationTile(occupiedTiles)) return;
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator.towardCore(
            getArea().width,
            getArea().height,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
        StationCoreDirectionIndicator.draw(
            arrow,
            EnumColors.MAP_COLOR_TEXT_TITLE.getColor(),
            EnumColors.MAP_COLOR_STATION_TILE_BORDER_HOVERED.getColor());
    }

    private boolean hasVisibleStationTile(Set<StationTileCoord> occupiedTiles) {
        for (StationTileCoord coord : occupiedTiles) {
            if (StationCoreDirectionIndicator
                .tileIntersectsScreen(tileLocalX(coord), tileLocalY(coord), getArea().width, getArea().height)) {
                return true;
            }
        }
        return false;
    }

    private void drawFeatureTooltip(AutomatedFacility facility) {
        int localX = toLocalMouseX(getContext().getMouseX());
        int localY = toLocalMouseY(getContext().getMouseY());
        StationMapViewport.TilePosition coord = StationMapViewport.tilePositionAt(
            localX,
            localY,
            getArea().width,
            getArea().height,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
        if (coord == null) return;
        hoveredFeatureDefinitions.clear();
        for (PlanetaryFeatureKey key : facility.planetaryFeaturesAt(coord.dx(), coord.dy())) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            if (definition != null) hoveredFeatureDefinitions.add(definition);
        }
        List<PlanetaryFeatureDefinition> features = hoveredFeatureDefinitions;
        if (features.isEmpty()) return;

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int iconSize = 8;
        int iconGap = 4;
        int tooltipWidth = fr.getStringWidth("Features");
        for (PlanetaryFeatureDefinition feature : features) {
            tooltipWidth = Math.max(tooltipWidth, iconSize + iconGap + fr.getStringWidth(feature.displayName()));
        }
        tooltipWidth += 12;
        int tooltipHeight = 8 + (features.size() + 1) * (fr.FONT_HEIGHT + 2);
        int tooltipX = Math.min(localX + 10, getArea().width - tooltipWidth - 2);
        int tooltipY = Math.min(localY + 10, getArea().height - tooltipHeight - 2);
        tooltipX = Math.max(2, tooltipX);
        tooltipY = Math.max(2, tooltipY);
        BorderedRect.draw(
            tooltipX,
            tooltipY,
            tooltipWidth,
            tooltipHeight,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());
        int textY = tooltipY + 4;
        fr.drawStringWithShadow("Features", tooltipX + 6, textY, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
        textY += fr.FONT_HEIGHT + 2;
        for (PlanetaryFeatureDefinition feature : features) {
            PlanetaryFeatureOverlayRenderer.drawIcon(feature, tooltipX + 6, textY, iconSize);
            fr.drawStringWithShadow(
                feature.displayName(),
                tooltipX + 6 + iconSize + iconGap,
                textY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            textY += fr.FONT_HEIGHT + 2;
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
        ModuleShape footprint) {
        if (footprint == null) return;
        for (StationTileCoord tile : footprint.tiles(anchor)) {
            addOrthogonalCandidates(candidates, tile);
        }
    }

    private static void addFootprintAnchorsContaining(Set<StationTileCoord> anchors, Set<StationTileCoord> tiles,
        ModuleShape footprint) {
        if (footprint == null || tiles == null) return;
        for (StationTileCoord tile : tiles) {
            addFootprintAnchorsContaining(anchors, tile, footprint);
        }
    }

    private static void addFootprintAnchorsContaining(Set<StationTileCoord> anchors, StationTileCoord tile,
        ModuleShape footprint) {
        if (tile == null) return;
        if (footprint == ModuleShape.SINGLE) {
            anchors.add(tile);
            return;
        }
        for (StationTileCoord offset : footprint.tiles(StationTileCoord.CORE)) {
            int anchorDx = tile.dx() - offset.dx();
            int anchorDy = tile.dy() - offset.dy();
            if (anchorDx < StationTileCoord.MIN || anchorDx > StationTileCoord.MAX) continue;
            if (anchorDy < StationTileCoord.MIN || anchorDy > StationTileCoord.MAX) continue;
            StationTileCoord anchor = StationTileCoord.of(anchorDx, anchorDy);
            if (footprint.fitsAt(anchor)) anchors.add(anchor);
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
