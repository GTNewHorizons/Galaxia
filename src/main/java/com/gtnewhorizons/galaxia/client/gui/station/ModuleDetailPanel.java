package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
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
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.ICapacityModule;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
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
    private int viewRecipeBtnX = -1, viewRecipeBtnY, viewRecipeBtnW;
    private boolean showRecipeList;
    private final List<Integer> recipeRemoveRows = new ArrayList<>();
    private int recipeListY, recipeListH;
    private int recipeListX;

    public ModuleDetailPanel(StationMapWidget map) {
        this.map = map;
        listenGuiAction((IGuiAction.MousePressed) button -> {
            if (button != 0) return false;
            StationTileCoord sel = map.selection();
            if (sel == null) return false;
            int mx = getContext().getAbsMouseX();
            int my = getContext().getAbsMouseY();
            int rx = mx - getArea().rx;
            int ry = my - getArea().ry;

            // [Add Recipe] button
            if (recipeBtnX >= 0 && rx >= recipeBtnX
                && rx <= recipeBtnX + recipeBtnW
                && ry >= recipeBtnY
                && ry <= recipeBtnY + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) {
                AutomatedFacility f = resolveFacility();
                if (f != null) {
                    PlacedTile t = f.stationLayout()
                        .get(sel);
                    if (t != null && t.module() != null
                        && t.module()
                            .component() instanceof IRecipeModule) {
                        RecipeInputScreen.open(
                            map.assetId(),
                            f.modules()
                                .indexOf(t.module()),
                            t.module());
                    }
                }
                return true;
            }

            // [View Recipes] button — toggle inline recipe list
            if (viewRecipeBtnX >= 0 && rx >= viewRecipeBtnX
                && rx <= viewRecipeBtnX + viewRecipeBtnW
                && ry >= viewRecipeBtnY
                && ry <= viewRecipeBtnY + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) {
                showRecipeList = !showRecipeList;
                return true;
            }

            // [Remove] in recipe list
            if (showRecipeList) {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                for (int i = 0; i < recipeRemoveRows.size(); i++) {
                    int rowY = recipeRemoveRows.get(i);
                    if (ry >= rowY && ry < rowY + fr.FONT_HEIGHT + 3) {
                        String removeLabel = "[Remove]";
                        int removeX = getArea().width - CONTENT_PADDING - fr.getStringWidth(removeLabel);
                        if (rx >= removeX && rx <= removeX + fr.getStringWidth(removeLabel)) {
                            AutomatedFacility f = resolveFacility();
                            if (f != null) {
                                PlacedTile t = f.stationLayout()
                                    .get(sel);
                                if (t != null && t.module() != null) {
                                    CelestialClient.updateModuleRecipeSlot(
                                        map.assetId(),
                                        f.modules()
                                            .indexOf(t.module()),
                                        AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT,
                                        (byte) i,
                                        null);
                                }
                            }
                            return true;
                        }
                    }
                }
            }

            return false;
        });
    }

    @Override
    public boolean canHoverThrough() {
        return recipeBtnX < 0 && viewRecipeBtnX < 0;
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
            FontRenderer fr2 = Minecraft.getMinecraft().fontRenderer;

            // [Add Recipe] button
            String addLabel = "[Add Recipe]";
            recipeBtnX = x + CONTENT_PADDING;
            recipeBtnY = lineY;
            recipeBtnW = fr2.getStringWidth(addLabel);
            fr2.drawStringWithShadow(addLabel, recipeBtnX, recipeBtnY, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

            // [View Recipes] / [Hide Recipes] toggle — right-aligned
            String viewLabel = showRecipeList ? "[Hide Recipes]" : "[View Recipes]";
            viewRecipeBtnX = x + width - CONTENT_PADDING - fr2.getStringWidth(viewLabel);
            viewRecipeBtnY = lineY;
            viewRecipeBtnW = fr2.getStringWidth(viewLabel);
            fr2.drawStringWithShadow(
                viewLabel,
                viewRecipeBtnX,
                viewRecipeBtnY,
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

            lineY += fr2.FONT_HEIGHT + 3;

            // Inline recipe list when toggled on
            if (showRecipeList) {
                recipeRemoveRows.clear();
                lineY += SECTION_GAP + 4;

                RecipeConfig cfg = ((IRecipeModule) module.component()).getRecipeConfig();
                List<RecipeSlot> slots = cfg != null ? cfg.slots()
                    .toList() : List.of();

                int listWidth = width - CONTENT_PADDING * 2;
                recipeListX = x + CONTENT_PADDING;
                recipeListY = lineY;

                if (slots.isEmpty()) {
                    drawLine("No recipes configured", recipeListX, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                    lineY += Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 3;
                } else {
                    for (int i = 0; i < slots.size(); i++) {
                        RecipeSlot slot = slots.get(i);
                        String label = "#" + i + " " + (slot.enabled() ? "[ON] " : "[OFF] ");
                        if (slot.inputGuard() != 0 || slot.outputGuard() != Integer.MAX_VALUE) {
                            label += " in:" + slot.inputGuard() + " out:" + slot.outputGuard();
                        }
                        int enabledColor = slot.enabled() ? EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor()
                            : EnumColors.MAP_COLOR_TEXT_DANGER.getColor();
                        FontRenderer fr3 = Minecraft.getMinecraft().fontRenderer;
                        fr3.drawStringWithShadow(label, recipeListX, lineY, enabledColor);
                        String rmLabel = "[Remove]";
                        int rmX = x + width - CONTENT_PADDING - fr3.getStringWidth(rmLabel);
                        fr3.drawStringWithShadow(rmLabel, rmX, lineY, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
                        recipeRemoveRows.add(lineY);
                        lineY += fr3.FONT_HEIGHT + 3;
                    }
                }
                recipeListH = lineY - recipeListY;
            }
        } else {
            recipeBtnX = -1;
            viewRecipeBtnX = -1;
            showRecipeList = false;
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
