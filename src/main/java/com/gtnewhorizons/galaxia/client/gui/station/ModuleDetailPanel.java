package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeInputScreen;
import com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeSlotListWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.ICapacityModule;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleDetailPanel extends ParentWidget<ModuleDetailPanel> {

    private static final int CONTENT_PADDING = 10;
    private static final int SECTION_GAP = 4;

    private final StationMapWidget map;
    private StationTileCoord lastCoveredAnchor;
    private boolean lastCoveredResult;
    private int recipeBtnX = -1, recipeBtnY, recipeBtnW;

    public ModuleDetailPanel(StationMapWidget map) {
        this.map = map;
        listenGuiAction((IGuiAction.MousePressed) button -> {
            if (button != 0 || recipeBtnX < 0) return false;
            StationTileCoord sel = map.selection();
            if (sel == null) return false;
            int mx = getContext().getAbsMouseX();
            int my = getContext().getAbsMouseY();
            int rx = mx - getArea().rx;
            int ry = my - getArea().ry;
            if (rx >= recipeBtnX && rx <= recipeBtnX + recipeBtnW
                && ry >= recipeBtnY
                && ry <= recipeBtnY + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) {
                AutomatedFacility f = resolveFacility();
                if (f != null) {
                    PlacedTile t = f.stationLayout()
                        .get(sel);
                    if (t != null && t.module() != null
                        && t.module()
                            .component() instanceof IRecipeModule) {
                        RecipeInputScreen.open(map.assetId(), f.modules()
                            .indexOf(t.module()), t.module());
                    }
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean canHoverThrough() {
        return recipeBtnX < 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        StationTileCoord selected = map.selection();
        if (selected == null) return;

        AutomatedFacility facility = resolveFacility();
        if (facility == null) return;

        StationLayout layout = facility.stationLayout();
        if (layout == null) return;

        PlacedTile tile = layout.get(selected);
        if (tile == null || tile.isCore()) return;

        ModuleInstance module = tile.module();
        if (module == null) return;

        CelestialAsset.ID facilityId = map.assetId();

        int x = 0;
        int y = 0;
        int width = getArea().width;
        int height = getArea().height;

        BorderedRect.draw(
            x,
            y,
            width,
            height,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int lineY = y + CONTENT_PADDING;

        lineY = drawLine(
            "Module: " + module.kind()
                .name(),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        StationTileCoord modAnchor = module.anchor();
        if (module.kind()
            .isCapacityModule()) {
            if (module.component() instanceof ICapacityModule icm) {
                long baseCapacity = icm.baseCapacityForTier(module.tier());
                int neighborCount = StationLayout.countOrthogonalNeighbors(layout, modAnchor, module.kind());
                long effectiveCapacity = Math.round(baseCapacity * (1.0 + 0.5 * neighborCount));
                long clusterTotal = 0;
                if (facilityId != null) {
                    List<CapacityCluster> clusters = GalaxiaAPI.getCapacityClusters(facilityId, module.kind());
                    for (CapacityCluster cluster : clusters) {
                        if (cluster.members()
                            .contains(modAnchor)) {
                            clusterTotal = cluster.effectiveCapacity();
                            break;
                        }
                    }
                }
                lineY += SECTION_GAP;
                lineY = drawLine(
                    "Base capacity: " + baseCapacity,
                    x + CONTENT_PADDING,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
                lineY = drawLine(
                    "Neighbors: " + neighborCount,
                    x + CONTENT_PADDING,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor());
                lineY = drawLine(
                    "Capacity: " + effectiveCapacity + " / " + clusterTotal,
                    x + CONTENT_PADDING,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            }
        }

        if (facilityId != null) {
            StationTileCoord curAnchor = module.anchor();
            if (!Objects.equals(curAnchor, lastCoveredAnchor)) {
                lastCoveredAnchor = curAnchor;
                lastCoveredResult = false;
                Set<StationTileCoord> coverage = GalaxiaAPI.getMaintenanceCoverage(facilityId);
                for (StationTileCoord tc : module.shape()
                    .tiles(curAnchor)) {
                    if (coverage.contains(tc)) {
                        lastCoveredResult = true;
                        break;
                    }
                }
            }
            if (lastCoveredResult) {
                lineY += SECTION_GAP;
                drawLine(
                    "Maintenance Bay: -20% upkeep",
                    x + CONTENT_PADDING,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            }
        }

        if (module.component() instanceof IRecipeModule) {
            lineY += SECTION_GAP;
            RecipeSlotListWidget widget = new RecipeSlotListWidget(module);
            lineY = widget.draw(x + CONTENT_PADDING, lineY, width - CONTENT_PADDING * 2);
            recipeBtnX = widget.addRecipeX;
            recipeBtnY = widget.addRecipeY;
            recipeBtnW = widget.addRecipeW;
        } else {
            recipeBtnX = -1;
        }
    }

    private static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    private @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID id = map.assetId();
        return id != null && CelestialClient.getByAssetId(id) instanceof AutomatedFacility f ? f : null;
    }
}
