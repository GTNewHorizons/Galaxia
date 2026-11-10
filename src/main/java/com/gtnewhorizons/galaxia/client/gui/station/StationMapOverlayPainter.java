package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.layer.PlanetaryFeatureOverlayRenderer;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapOverlayPainter {

    private static final int ALERT_ICON_SIZE = 8;
    private static final int FEATURE_ICON_SIZE = 8;
    private static final ResourceLocation DEFAULT_ALERT_ICON = EnumTextures.ICON_STATION_ALERT_WARNING.get();
    private static final ResourceLocation DEFAULT_RED_ALERT_ICON = EnumTextures.ICON_STATION_ALERT_ERROR.get();

    private StationMapOverlayPainter() {}

    static void drawFeatureOverlay(AutomatedFacility facility, StationMapFrame frame,
        List<StationMapFrame.TilePosition> visibleFeatureTiles,
        PlanetaryFeatureOverlayRenderer.VisibleFeatures featureProjection) {
        frame.collectVisibleTilePositions(visibleFeatureTiles);
        var features = featureProjection.project(facility, visibleFeatureTiles);
        for (int i = 0; i < visibleFeatureTiles.size(); i++) {
            StationMapFrame.TilePosition coord = visibleFeatureTiles.get(i);
            PlanetaryFeatureOverlayRenderer
                .draw(frame.tileLocalX(coord.dx()), frame.tileLocalY(coord.dy()), features.get(i));
        }
    }

    static void drawModuleAlerts(Map<StationTileCoord, PlacedTile> tiles,
        Map<ModuleInstance.ID, StationModuleAlert> moduleAlerts, StationMapFrame frame) {
        if (moduleAlerts.isEmpty()) return;
        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            ModuleInstance module = moduleOf(entry.getValue());
            if (module == null) continue;
            StationModuleAlert alert = moduleAlerts.get(module.id);
            if (alert == null) continue;
            if (!entry.getKey()
                .equals(alertBadgeCoord(module))) continue;
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
        StationTileRenderer.drawHoverOverlay(x, y, StationMapFrame.TILE_SIZE);
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
        StationTileRenderer.drawSelectionOverlay(x, y, StationMapFrame.TILE_SIZE);
    }

    static void drawMaintenanceBayCoverage(@Nullable StationTileCoord selected, Map<StationTileCoord, PlacedTile> tiles,
        StationMapFrame frame) {
        List<MaintenanceCoverageTarget> targets = maintenanceCoverageTargets(selected, tiles);
        int fillColor = EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_FILL.getColor();
        for (MaintenanceCoverageTarget target : targets) {
            ModuleInstance module = target.module();
            if (module != null) {
                ModuleFootprintProjection
                    .drawFilled(module.shape(), module.anchor(), module.rotation(), frame, fillColor);
            } else if (target.tile() != null) {
                int x = frame.tileLocalX(target.tile());
                int y = frame.tileLocalY(target.tile());
                Gui.drawRect(x, y, x + StationMapFrame.TILE_SIZE, y + StationMapFrame.TILE_SIZE, fillColor);
            }
        }
        for (MaintenanceCoverageTarget target : targets) {
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
                    StationMapFrame.TILE_SIZE,
                    StationMapFrame.TILE_SIZE,
                    EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_FILL.getColor(),
                    EnumColors.MAP_COLOR_STATION_DEBUG_NEIGHBOR_BORDER.getColor());
            }
        }
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
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator.towardCore(frame);
        StationCoreDirectionIndicator.draw(arrow);
    }

    /** The map's tooltip: the planetary features of the tile under the cursor, then the alert of its module */
    static void hoverTooltip(RichTooltip tooltip, AutomatedFacility facility, Map<StationTileCoord, PlacedTile> tiles,
        @Nullable StationTileCoord hovered, int localMouseX, int localMouseY, StationMapFrame frame) {
        StationMapFrame.TilePosition coord = frame.tilePositionAt(localMouseX, localMouseY);
        List<PlanetaryFeatureDefinition> features = coord == null ? List.of()
            : PlanetaryFeatureRegistry.definitionsFor(facility.planetaryFeaturesAt(coord.dx(), coord.dy()));
        if (!features.isEmpty()) {
            tooltip.addLine(StatCollector.translateToLocal("galaxia.gui.station.tooltip.features"));
            for (PlanetaryFeatureDefinition feature : features) {
                IDrawable icon = (context, x, y, width, height, theme) -> PlanetaryFeatureOverlayRenderer
                    .drawIcon(feature, x, y, Math.min(width, height));
                tooltip.add(
                    icon.asIcon()
                        .size(FEATURE_ICON_SIZE))
                    .space()
                    .addLine(
                        IKey.str(feature.displayName())
                            .style(EnumChatFormatting.GRAY));
            }
        }
        ModuleInstance module = hovered == null ? null : moduleOf(tiles.get(hovered));
        StationModuleAlert alert = module == null ? null
            : StationModuleAlert.alerts(facility)
                .get(module.id);
        if (alert == null) return;
        tooltip.addLine(
            IKey.str(StatCollector.translateToLocal(alert.title()))
                .color(
                    alert.severity() == StationModuleAlert.Severity.RED ? GalaxiaGuiStyle.BAD : GalaxiaGuiStyle.WARN))
            .addLine(
                IKey.str(StatCollector.translateToLocal(alert.message()))
                    .style(EnumChatFormatting.GRAY));
    }

    static StationTileCoord alertBadgeCoord(ModuleInstance module) {
        return ModuleFootprintProjection.firstTile(module.shape(), module.anchor(), module.rotation());
    }

    static void drawDeconstructModuleOverlay(ModuleInstance module, boolean selected, StationMapFrame frame) {
        int color = selected ? EnumColors.MAP_COLOR_STATION_PICKER_DECONSTRUCT_SELECTED.getColor()
            : EnumColors.MAP_COLOR_STATION_PICKER_COMPATIBLE.getColor();
        drawModuleOverlay(module, color, frame);
    }

    private static void drawModuleOverlay(ModuleInstance module, int color, StationMapFrame frame) {
        if (module == null) return;
        ModuleFootprintProjection.drawOutline(module.shape(), module.anchor(), module.rotation(), frame, color);
    }

    private static boolean hasVisibleStationTile(Set<StationTileCoord> occupiedTiles, StationMapFrame frame) {
        for (StationTileCoord coord : occupiedTiles) {
            if (frame.tileIntersectsScreen(frame.tileLocalX(coord), frame.tileLocalY(coord))) {
                return true;
            }
        }
        return false;
    }

    private static void drawModuleAlertIcon(int tileX, int tileY, StationModuleAlert alert) {
        ResourceLocation icon = alert.icon() != null ? alert.icon() : defaultAlertIcon(alert.severity());
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(icon);
        com.cleanroommc.modularui.utils.GlStateManager.enableBlend();
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
        com.cleanroommc.modularui.drawable.GuiDraw.drawTexture(
            tileX + 2,
            tileY + 2,
            tileX + 2 + ALERT_ICON_SIZE,
            tileY + 2 + ALERT_ICON_SIZE,
            0f,
            0f,
            1f,
            1f);
    }

    private static @Nullable ModuleInstance moduleOf(@Nullable PlacedTile tile) {
        return tile == null ? null : tile.module();
    }

    private static ResourceLocation defaultAlertIcon(StationModuleAlert.Severity severity) {
        return severity == StationModuleAlert.Severity.RED ? DEFAULT_RED_ALERT_ICON : DEFAULT_ALERT_ICON;
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
