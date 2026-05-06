package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.ICapacityModule;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleDetailPanel extends ParentWidget<ModuleDetailPanel> {

    private static final int CONTENT_PADDING = 10;
    private static final int SECTION_GAP = 4;
    private static final int BUTTON_H = 16;
    private static final int ACTION_X = 10;
    private static final int ACTION_Y = 40;
    private static final int ACTION_BUTTON_WIDTH = 70;
    private static final int CHARGE_BAR_TOP_OFFSET = 2;
    private static final int CHARGE_BAR_HEIGHT = 8;
    private static final int CHARGE_BAR_BOTTOM_GAP = 3;

    private final StationMapWidget map;
    private StationTileCoord lastCoveredAnchor;
    private boolean lastCoveredResult;
    private final ModuleConfigModalController configController;

    public ModuleDetailPanel(StationMapWidget map, ModuleConfigModalController configController) {
        this.map = map;
        this.configController = configController;
        child(
            createPanelButton(() -> "Configure", this::hasMinerSelected, this::openMinerVoidConfig)
                .pos(ACTION_X, ACTION_Y)
                .size(ACTION_BUTTON_WIDTH, BUTTON_H));
        child(
            createPanelButton(() -> "Configure", this::hasHammerSelected, this::openHammerConfig)
                .pos(ACTION_X, ACTION_Y)
                .size(ACTION_BUTTON_WIDTH, BUTTON_H));
        child(
            createPanelButton(() -> "Configure", this::hasRecipeModuleSelected, this::openRecipeInput)
                .pos(ACTION_X, ACTION_Y)
                .size(ACTION_BUTTON_WIDTH, BUTTON_H));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
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

        lineY = Math.max(lineY, ACTION_Y + BUTTON_H + SECTION_GAP);

        if (module.component() instanceof ModuleHammer hammer) {
            lineY += SECTION_GAP;
            lineY = drawHammerOverview(module, hammer, x, lineY, width);
        }

        if (module.component() instanceof IRecipeModule recipeModule) {
            lineY += SECTION_GAP;
            RecipeConfig cfg = recipeModule.getRecipeConfig();
            int slots = cfg == null ? 0
                : cfg.slots()
                    .toList()
                    .size();
            lineY = drawLine(
                "Recipes: " + slots,
                x + CONTENT_PADDING,
                lineY,
                EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        }
    }

    private static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    private int drawHammerOverview(ModuleInstance module, ModuleHammer hammer, int x, int y, int width) {
        int panelX = x + CONTENT_PADDING;
        int panelW = width - CONTENT_PADDING * 2;
        int lineY = y;
        HammerVariant variant = hammer.variant();
        ModuleTier tier = module.tier();
        int cooldown = ModuleHammer.cooldownTicks(variant, tier);
        int chargeTicks = ModuleHammer.chargeTicks(variant, tier);
        long shotEnergy = ModuleHammer.shotEnergyEu(variant);
        long chargeRate = ModuleHammer.chargeRateEuPerTick(variant, tier);
        lineY = drawLine("Hammer", panelX, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY = drawLine(
            "Variant: " + hammer.variant()
                .name()
                + "  Tier: "
                + module.tier()
                    .name(),
            panelX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = drawLine(
            "Shot: " + formatEu(shotEnergy) + " EU  Rate: " + formatEu(chargeRate) + " EU/t",
            panelX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = drawLine(
            "Cooldown: " + (cooldown / 20) + "s  Charge: " + (chargeTicks / 20) + "s",
            panelX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        int barX = panelX;
        int barY = lineY + CHARGE_BAR_TOP_OFFSET;
        int barW = panelW;
        int barH = CHARGE_BAR_HEIGHT;
        int chargeProgress = Math.min(Math.max(module.ticks(), 0), chargeTicks);
        int fillW = (int) ((long) barW * chargeProgress / chargeTicks);
        Gui.drawRect(barX, barY, barX + barW, barY + barH, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(
            barX,
            barY,
            barX + fillW,
            barY + barH,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
        return barY + barH + CHARGE_BAR_BOTTOM_GAP;
    }

    private ButtonWidget<?> createPanelButton(Supplier<String> labelSupplier, BooleanSupplier enabledSupplier,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean()) return;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String label = fr.trimStringToWidth(labelSupplier.get(), w - 4);
                int color = EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor();
                int textW = fr.getStringWidth(label);
                fr.drawStringWithShadow(label, x + (w - textW) / 2, y + (h - fr.FONT_HEIGHT) / 2 + 1, color);
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            });
    }

    private static void drawButtonBackground(int x, int y, int w, int h, boolean enabled, boolean hovered) {
        if (!enabled) return;
        BorderedRect.draw(
            x,
            y,
            w,
            h,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
    }

    private boolean hasHammerSelected() {
        return selectedModule() instanceof SelectedModule selected
            && selected.module.component() instanceof ModuleHammer;
    }

    private boolean hasMinerSelected() {
        return selectedModule() instanceof SelectedModule selected
            && selected.module.component() instanceof ModuleMiner;
    }

    private boolean hasRecipeModuleSelected() {
        return selectedModule() instanceof SelectedModule selected
            && selected.module.component() instanceof IRecipeModule;
    }

    private void openHammerConfig() {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        if (!(selected.module.component() instanceof ModuleHammer)) return;
        configController.openHammer(selected.moduleIndex);
    }

    private void openMinerVoidConfig() {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        if (!(selected.module.component() instanceof ModuleMiner)) return;
        configController.openMinerVoid(selected.moduleIndex);
    }

    private void openRecipeInput() {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        if (!(selected.module.component() instanceof IRecipeModule)) return;
        RecipeInputScreen.open(map.assetId(), selected.moduleIndex, selected.module);
    }

    private @Nullable SelectedModule selectedModule() {
        StationTileCoord selected = map.selection();
        if (selected == null) return null;
        AutomatedFacility facility = resolveFacility();
        if (facility == null || map.assetId() == null) return null;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return null;
        PlacedTile tile = layout.get(selected);
        if (tile == null || tile.module() == null || tile.isCore()) return null;
        int moduleIndex = facility.modules()
            .indexOf(tile.module());
        if (moduleIndex < 0) return null;
        return new SelectedModule(facility, tile.module(), moduleIndex);
    }

    private static String formatEu(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private com.cleanroommc.modularui.api.drawable.IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID id = map.assetId();
        return id != null && CelestialClient.getByAssetId(id) instanceof AutomatedFacility f ? f : null;
    }

    private record SelectedModule(AutomatedFacility facility, ModuleInstance module, int moduleIndex) {}
}
