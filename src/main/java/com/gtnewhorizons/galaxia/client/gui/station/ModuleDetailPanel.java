package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleDetailPanel extends ParentWidget<ModuleDetailPanel> {

    private static final int CONTENT_PADDING = 10;
    private static final int SECTION_GAP = 4;
    private static final int CHARGE_BAR_TOP_OFFSET = 2;
    private static final int CHARGE_BAR_HEIGHT = 8;
    private static final int CHARGE_BAR_BOTTOM_GAP = 3;

    private final StationMapWidget map;
    private StationTileCoord lastCoveredAnchor;
    private boolean lastCoveredResult;
    private final @Nullable StationTilePickerController tilePickerController;

    public ModuleDetailPanel(StationMapWidget map) {
        this(map, null);
    }

    public ModuleDetailPanel(StationMapWidget map, @Nullable StationTilePickerController tilePickerController) {
        this.map = map;
        this.tilePickerController = tilePickerController;
    }

    @Override
    public boolean canHoverThrough() {
        return isPickerActive();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (isPickerActive()) return;
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
            {
                long baseCapacity = module.baseCapacity();
                int neighborCount = StationLayout.countOrthogonalNeighbors(layout, modAnchor, module.kind());
                long effectiveCapacity = Math.round(baseCapacity * (1.0 + 0.5 * neighborCount));
                lineY += SECTION_GAP;
                lineY = drawLine(
                    "Base: " + baseCapacity,
                    x + CONTENT_PADDING,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
                lineY = drawLine(
                    "Capacity: " + effectiveCapacity,
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

        lineY = drawPlanetaryFeatures(facility, module, x, lineY);

        if (module.component() instanceof ModuleHammer hammer) {
            lineY += SECTION_GAP;
            lineY = drawHammerOverview(facility, module, hammer, x, lineY, width);
        }

        if (module.component() instanceof IRecipeModule recipeModule) {
            lineY += SECTION_GAP;
            RecipeConfig cfg = recipeModule.getRecipeConfig();
            int slots = cfg == null ? 0
                : cfg.savedRecipes()
                    .toList()
                    .size();
            lineY = drawLine(
                "Recipes: " + slots,
                x + CONTENT_PADDING,
                lineY,
                EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        }
    }

    private int drawPlanetaryFeatures(AutomatedFacility facility, ModuleInstance module, int x, int y) {
        java.util.LinkedHashSet<PlanetaryFeatureKey> features = new java.util.LinkedHashSet<>();
        for (StationTileCoord coord : module.shape()
            .tiles(module.anchor())) {
            features.addAll(facility.planetaryFeaturesAt(coord));
        }
        if (features.isEmpty()) return y;
        y += SECTION_GAP;
        for (PlanetaryFeatureKey key : features) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            String name = definition != null ? definition.displayName() : key.toString();
            y = drawLine("Feature: " + name, x + CONTENT_PADDING, y, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        }
        for (FeatureContribution contribution : facility.featureContributions(module)) {
            if (!contribution.effectLine()
                .isBlank()) {
                y = drawLine(
                    contribution.effectLine(),
                    x + CONTENT_PADDING,
                    y,
                    EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            }
        }
        return y;
    }

    private static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    private int drawHammerOverview(AutomatedFacility facility, ModuleInstance module, ModuleHammer hammer, int x, int y,
        int width) {
        int panelX = x + CONTENT_PADDING;
        int panelW = width - CONTENT_PADDING * 2;
        int lineY = y;
        HammerVariant variant = hammer.variant();
        ModuleTier tier = module.tier();
        int chargeTicks = hammer.chargeTicks(module);
        long bufferCapacity = hammer.energyCapacity();
        long chargeRate = hammer.chargeRate(module);
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
            "Buffer: " + formatEu(hammer.energyStored())
                + "/"
                + formatEu(bufferCapacity)
                + " EU  Rate: "
                + formatEu(chargeRate)
                + " EU/t",
            panelX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = drawLine(
            "Charge: " + (chargeTicks / 20) + "s  Energy per dV: " + formatEu(ModuleHammer.EU_PER_DV) + " EU",
            panelX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        HammerDispatchStatus.Status dispatchStatus = HammerDispatchStatus
            .evaluate(facility, module, CelestialClient.allOutposts(), GalaxiaCelestialAPI.currentOrbitalTime());
        lineY = drawLine(
            hammerDispatchStatusLine(dispatchStatus),
            panelX,
            lineY,
            dispatchStatus.code() == HammerDispatchStatus.Code.READY ? EnumColors.MAP_COLOR_TEXT_BODY.getColor()
                : EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        if (module.operationOrNull() != null && !module.operationOrNull()
            .phase()
            .isTerminal()) {
            lineY = drawLine(
                facility.isItemInventoryFull() && module.operationOrNull()
                    .phase() == ModuleOperationPhase.REFUNDING
                        ? "Inventory full; refund paused"
                        : "Operation: " + module.operationOrNull()
                            .phase()
                            .name(),
                panelX,
                lineY,
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            IModuleOperation activeSpec = module.operationOrNull()
                .plan()
                .spec();
            if (activeSpec instanceof HammerModuleOperation hammerSpec) {
                lineY = drawLine(
                    "Target: " + hammerSpec.targetVariantKey()
                        + " "
                        + hammerSpec.targetTier()
                            .name(),
                    panelX,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            }
        }

        int barX = panelX;
        int barY = lineY + CHARGE_BAR_TOP_OFFSET;
        int barW = panelW;
        int barH = CHARGE_BAR_HEIGHT;
        int fillW = (int) (barW * hammer.energyStored() / Math.max(1L, bufferCapacity));
        Gui.drawRect(barX, barY, barX + barW, barY + barH, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(
            barX,
            barY,
            barX + fillW,
            barY + barH,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
        return barY + barH + CHARGE_BAR_BOTTOM_GAP;
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

    private static String hammerDispatchStatusLine(HammerDispatchStatus.Status status) {
        return switch (status.code()) {
            case READY -> "Dispatch: ready";
            case WAITING_FOR_REQUEST -> "Dispatch: waiting for request";
            case NO_EXPORT_CONFIG -> "Dispatch: export disabled";
            case NO_SURPLUS_AFTER_RESERVE -> "Dispatch: no surplus after reserve";
            case ORDER_BELOW_PACKAGE_SIZE -> "Dispatch: order below package size " + status.sendAmount()
                + "/"
                + status.orderSize();
            case NEED_BIG_HAMMER -> "Dispatch: need BIG Hammer";
            case ROUTE_UNAVAILABLE -> "Dispatch: route unavailable";
            case BLOCKED_BY_DV_LIMIT -> "Dispatch: blocked by dV limit";
            case BLOCKED_BY_TOF_LIMIT -> "Dispatch: blocked by TOF limit";
            case NEED_ENERGY -> "Dispatch: need " + formatEu(status.requiredEnergy())
                + " EU, buffer "
                + formatEu(status.storedEnergy());
        };
    }

    private com.cleanroommc.modularui.api.drawable.IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID id = map.assetId();
        return id != null && CelestialClient.getByAssetId(id) instanceof AutomatedFacility f ? f : null;
    }

    private boolean isPickerActive() {
        return tilePickerController != null && tilePickerController.isActive();
    }

    private record SelectedModule(AutomatedFacility facility, ModuleInstance module, int moduleIndex) {}
}
