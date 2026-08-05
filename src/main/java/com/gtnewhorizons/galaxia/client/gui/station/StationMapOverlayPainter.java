package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.layer.PlanetaryFeatureOverlayRenderer;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapOverlayPainter {

    private static final int ALERT_ICON_SIZE = 8;
    private static final ResourceLocation DEFAULT_ALERT_ICON = EnumTextures.ICON_STATION_ALERT_WARNING.get();
    private static final ResourceLocation DEFAULT_RED_ALERT_ICON = EnumTextures.ICON_STATION_ALERT_ERROR.get();

    private StationMapOverlayPainter() {}

    static void drawFeatureOverlay(AutomatedFacility facility, StationMapFrame frame,
        List<StationMapViewport.TilePosition> visibleFeatureTiles) {
        StationMapViewport.collectVisibleTilePositions(
            frame.widgetWidth(),
            frame.widgetHeight(),
            frame.contentLeft(),
            frame.contentRightPadding(),
            frame.contentVerticalPadding(),
            frame.panX(),
            frame.panY(),
            visibleFeatureTiles);
        for (StationMapViewport.TilePosition coord : visibleFeatureTiles) {
            PlanetaryFeatureOverlayRenderer.draw(
                frame.tileLocalX(coord.dx()),
                frame.tileLocalY(coord.dy()),
                facility.planetaryFeaturesAt(coord.dx(), coord.dy()));
        }
    }

    static void drawModuleAlerts(Map<StationTileCoord, PlacedTile> tiles,
        Map<ModuleInstance.ID, List<StationModuleAlert>> moduleAlerts, StationMapFrame frame) {
        if (moduleAlerts.isEmpty()) return;
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            ModuleInstance module = moduleOf(entry.getValue());
            if (module == null || !entry.getKey()
                .equals(alertBadgeCoord(module, tiles))) {
                continue;
            }
            StationModuleAlert alert = firstAlert(moduleAlerts, module);
            if (alert == null) continue;
            drawModuleAlertIcon(frame.tileLocalX(entry.getKey()), frame.tileLocalY(entry.getKey()), alert);
        }
    }

    static void drawHoverOverlay(StationTileCoord coord, Map<StationTileCoord, PlacedTile> tiles,
        StationMapFrame frame) {
        PlacedTile placedTile = tiles.get(coord);
        ModuleInstance module = moduleOf(placedTile);
        if (module != null && module.shape()
            .tileCount() > 1) {
            drawModuleOverlay(module, EnumColors.MAP_COLOR_STATION_TILE_BORDER_HOVERED.getColor(), frame);
            return;
        }
        int x = frame.tileLocalX(coord);
        int y = frame.tileLocalY(coord);
        StationTileRenderer.drawHoverOverlay(x, y, StationMapViewport.TILE_SIZE);
    }

    static void drawSelectionOverlay(StationTileCoord coord, Map<StationTileCoord, PlacedTile> tiles,
        StationMapFrame frame) {
        PlacedTile placedTile = tiles.get(coord);
        ModuleInstance module = moduleOf(placedTile);
        if (module != null && module.shape()
            .tileCount() > 1) {
            drawModuleOverlay(module, EnumColors.MAP_COLOR_STATION_TILE_BORDER_SELECTED.getColor(), frame);
            return;
        }
        int x = frame.tileLocalX(coord);
        int y = frame.tileLocalY(coord);
        StationTileRenderer.drawSelectionOverlay(x, y, StationMapViewport.TILE_SIZE);
    }

    static void drawMaintenanceBayCoverage(@Nullable StationTileCoord selected, Map<StationTileCoord, PlacedTile> tiles,
        StationMapFrame frame) {
        drawSegments(
            maintenanceCoverageFillSegments(selected, tiles, frame),
            EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_FILL.getColor());
        for (MaintenanceCoverageTarget target : maintenanceCoverageTargets(selected, tiles)) {
            ModuleInstance module = target.module();
            if (module != null) {
                drawModuleOverlay(module, EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_BORDER.getColor(), frame);
                continue;
            }
            StationTileCoord coord = target.tile();
            if (coord != null) {
                BorderedRect.draw(
                    frame.tileLocalX(coord),
                    frame.tileLocalY(coord),
                    StationMapViewport.TILE_SIZE,
                    StationMapViewport.TILE_SIZE,
                    EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_FILL.getColor(),
                    EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_BORDER.getColor());
            }
        }
    }

    static List<ModuleFootprintProjection.Segment> maintenanceCoverageFillSegments(@Nullable StationTileCoord selected,
        Map<StationTileCoord, PlacedTile> tiles, StationMapFrame frame) {
        List<ModuleFootprintProjection.Segment> segments = new ArrayList<>();
        for (MaintenanceCoverageTarget target : maintenanceCoverageTargets(selected, tiles)) {
            ModuleInstance module = target.module();
            if (module != null) {
                segments.addAll(
                    ModuleFootprintProjection
                        .filledSegments(module.shape(), module.anchor(), module.rotation(), frame));
                continue;
            }
            StationTileCoord tile = target.tile();
            if (tile != null) {
                segments.add(
                    new ModuleFootprintProjection.Segment(
                        frame.tileLocalX(tile),
                        frame.tileLocalY(tile),
                        StationMapViewport.TILE_SIZE,
                        StationMapViewport.TILE_SIZE));
            }
        }
        return segments;
    }

    static List<MaintenanceCoverageTarget> maintenanceCoverageTargets(@Nullable StationTileCoord selected,
        Map<StationTileCoord, PlacedTile> tiles) {
        if (selected == null || !tiles.containsKey(selected)) return List.of();
        PlacedTile selectedTile = tiles.get(selected);
        ModuleInstance selectedModule = selectedTile != null ? selectedTile.module() : null;
        if (selectedModule == null || selectedModule.kind() != FacilityModuleKind.MAINTENANCE_BAY) return List.of();

        List<MaintenanceCoverageTarget> targets = new ArrayList<>();
        Set<ModuleInstance.ID> targetedModules = new HashSet<>();
        StationTileCoord anchor = selectedModule.anchor();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = anchor.dx() + dx;
                int ny = anchor.dy() + dy;
                if (nx < StationTileCoord.MIN || nx > StationTileCoord.MAX
                    || ny < StationTileCoord.MIN
                    || ny > StationTileCoord.MAX) continue;
                StationTileCoord coord = StationTileCoord.of(nx, ny);
                ModuleInstance module = moduleOf(tiles.get(coord));
                if (module != null) {
                    if (targetedModules.add(module.id)) targets.add(MaintenanceCoverageTarget.module(module));
                } else {
                    targets.add(MaintenanceCoverageTarget.tile(coord));
                }
            }
        }
        return targets;
    }

    static void drawCoreDirectionIndicator(Set<StationTileCoord> occupiedTiles, StationMapFrame frame) {
        if (hasVisibleStationTile(occupiedTiles, frame)) return;
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator.towardCore(
            frame.widgetWidth(),
            frame.widgetHeight(),
            frame.contentLeft(),
            frame.contentRightPadding(),
            frame.contentVerticalPadding(),
            frame.panX(),
            frame.panY());
        StationCoreDirectionIndicator.draw(
            arrow,
            EnumColors.MAP_COLOR_TEXT_TITLE.getColor(),
            EnumColors.MAP_COLOR_STATION_TILE_BORDER_HOVERED.getColor());
    }

    static void drawFeatureTooltip(AutomatedFacility facility, StationFeatureSurface featureSurface, int localMouseX,
        int localMouseY, StationMapFrame frame, List<PlanetaryFeatureDefinition> hoveredFeatureDefinitions) {
        StationMapViewport.TilePosition coord = StationMapViewport.tilePositionAt(
            localMouseX,
            localMouseY,
            frame.widgetWidth(),
            frame.widgetHeight(),
            frame.contentLeft(),
            frame.contentRightPadding(),
            frame.contentVerticalPadding(),
            frame.panX(),
            frame.panY());
        if (coord == null) return;
        List<PlanetaryFeatureDefinition> features = featureSurface
            .hoverDefinitions(facility, coord, hoveredFeatureDefinitions);
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
        int tooltipX = Math.min(localMouseX + 10, frame.widgetWidth() - tooltipWidth - 2);
        int tooltipY = Math.min(localMouseY + 10, frame.widgetHeight() - tooltipHeight - 2);
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

    static void drawModuleAlertTooltip(Map<StationTileCoord, PlacedTile> tiles,
        Map<ModuleInstance.ID, List<StationModuleAlert>> moduleAlerts, @Nullable StationTileCoord hovered,
        int localMouseX, int localMouseY, StationMapFrame frame) {
        if (moduleAlerts.isEmpty() || hovered == null) return;
        PlacedTile tile = tiles.get(hovered);
        ModuleInstance module = moduleOf(tile);
        if (module == null) return;
        List<StationModuleAlert> alerts = moduleAlerts.get(module.id);
        if (alerts == null || alerts.isEmpty()) return;

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int maxTextWidth = Math.max(40, Math.min(180, frame.widgetWidth() - 20));
        int tooltipWidth = 40;
        for (StationModuleAlert alert : alerts) {
            tooltipWidth = Math.max(tooltipWidth, fr.getStringWidth(fr.trimStringToWidth(alert.title(), maxTextWidth)));
            tooltipWidth = Math
                .max(tooltipWidth, fr.getStringWidth(fr.trimStringToWidth(alert.message(), maxTextWidth)));
        }
        tooltipWidth += 12;
        int tooltipHeight = 8 + alerts.size() * (fr.FONT_HEIGHT * 2 + 6);
        int tooltipX = Math.min(localMouseX + 10, frame.widgetWidth() - tooltipWidth - 2);
        int tooltipY = Math.min(localMouseY + 10, frame.widgetHeight() - tooltipHeight - 2);
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

    static List<ModuleFootprintProjection.Segment> moduleOverlaySegments(ModuleInstance module, StationMapFrame frame) {
        if (module == null) return List.of();
        return ModuleFootprintProjection.outlineSegments(module.shape(), module.anchor(), module.rotation(), frame);
    }

    static StationTileCoord alertBadgeCoord(ModuleInstance module, Map<StationTileCoord, PlacedTile> tiles) {
        StationTileCoord best = module.anchor();
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            ModuleInstance tileModule = moduleOf(entry.getValue());
            if (tileModule == null || !module.id.equals(tileModule.id)) continue;
            StationTileCoord coord = entry.getKey();
            if (coord.dy() < best.dy() || coord.dy() == best.dy() && coord.dx() < best.dx()) {
                best = coord;
            }
        }
        return best;
    }

    static void drawDeconstructModuleOverlay(ModuleInstance module, boolean selected, StationMapFrame frame) {
        int color = selected ? EnumColors.MAP_COLOR_STATION_PICKER_DECONSTRUCT_SELECTED.getColor()
            : EnumColors.MAP_COLOR_STATION_PICKER_COMPATIBLE.getColor();
        drawModuleOverlay(module, color, frame);
    }

    private static void drawModuleOverlay(ModuleInstance module, int color, StationMapFrame frame) {
        drawSegments(moduleOverlaySegments(module, frame), color);
    }

    private static void drawSegments(List<ModuleFootprintProjection.Segment> segments, int color) {
        for (ModuleFootprintProjection.Segment segment : segments) {
            Gui.drawRect(
                segment.x(),
                segment.y(),
                segment.x() + segment.width(),
                segment.y() + segment.height(),
                color);
        }
    }

    private static boolean hasVisibleStationTile(Set<StationTileCoord> occupiedTiles, StationMapFrame frame) {
        for (StationTileCoord coord : occupiedTiles) {
            if (StationCoreDirectionIndicator.tileIntersectsScreen(
                frame.tileLocalX(coord),
                frame.tileLocalY(coord),
                frame.widgetWidth(),
                frame.widgetHeight())) {
                return true;
            }
        }
        return false;
    }

    private static void drawModuleAlertIcon(int tileX, int tileY, StationModuleAlert alert) {
        ResourceLocation icon = alert.icon() != null ? alert.icon() : defaultAlertIcon(alert.severity());
        ModuleConfigModalSupport.renderTextureIcon(icon, tileX + 2, tileY + 2, ALERT_ICON_SIZE, ALERT_ICON_SIZE);
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

    record MaintenanceCoverageTarget(@Nullable ModuleInstance module, @Nullable StationTileCoord tile) {

        static MaintenanceCoverageTarget module(ModuleInstance module) {
            return new MaintenanceCoverageTarget(module, null);
        }

        static MaintenanceCoverageTarget tile(StationTileCoord tile) {
            return new MaintenanceCoverageTarget(null, tile);
        }
    }
}
