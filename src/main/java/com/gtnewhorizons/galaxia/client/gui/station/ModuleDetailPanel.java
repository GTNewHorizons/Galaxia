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
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.ICapacityModule;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleDetailPanel extends ParentWidget<ModuleDetailPanel> {

    private static final int CONTENT_PADDING = 10;
    private static final int SECTION_GAP = 4;
    private static final int BUTTON_H = 16;
    private static final int ACTION_Y = 40;
    private static final int RECIPE_LIST_Y = 88;
    private static final int RECIPE_ROW_H = 12;
    private static final int MAX_RECIPE_BUTTONS = 32;

    private final StationMapWidget map;
    private StationTileCoord lastCoveredAnchor;
    private boolean lastCoveredResult;
    private boolean showRecipeList;
    private boolean showHammerConfig;

    public ModuleDetailPanel(StationMapWidget map) {
        this.map = map;
        child(createPanelButton(() -> "Hammer", this::hasHammerSelected, this::openHammerConfig).pos(10, ACTION_Y)
            .size(58, BUTTON_H));
        child(createPanelButton(() -> "Add Recipe", this::hasRecipeModuleSelected, this::openRecipeInput).pos(72, ACTION_Y)
            .size(62, BUTTON_H));
        child(createPanelButton(() -> showRecipeList ? "Hide Recipes" : "View Recipes", this::hasRecipeModuleSelected, () -> {
            showRecipeList = !showRecipeList;
            showHammerConfig = false;
        }).pos(138, ACTION_Y)
            .size(58, BUTTON_H));
        child(createPanelButton(this::hammerVariantLabel, this::canUseHammerConfigButtons, this::cycleHammerVariant).pos(10, 116)
            .size(70, BUTTON_H));
        child(createPanelButton(this::hammerTierLabel, this::canUseHammerConfigButtons, this::cycleHammerTier).pos(84, 116)
            .size(50, BUTTON_H));
        child(createPanelButton(() -> "Close", this::canUseHammerConfigButtons, () -> showHammerConfig = false).pos(138, 116)
            .size(50, BUTTON_H));
        for (int i = 0; i < MAX_RECIPE_BUTTONS; i++) {
            final int slotIndex = i;
            child(createPanelButton(() -> "Remove", () -> canRemoveRecipeSlot(slotIndex), () -> removeRecipeSlot(slotIndex))
                .pos(144, RECIPE_LIST_Y + slotIndex * RECIPE_ROW_H)
                .size(50, 10));
        }
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

        if (module.component() instanceof ModuleHammer hammer && showHammerConfig) {
            drawHammerConfig(module, hammer, x, 62, width);
            showRecipeList = false;
        }

        if (module.component() instanceof IRecipeModule) {
            lineY += SECTION_GAP;
            FontRenderer fr2 = Minecraft.getMinecraft().fontRenderer;

            drawLine("Recipes", x + CONTENT_PADDING, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());

            lineY += fr2.FONT_HEIGHT + 3;

            // Inline recipe list when toggled on
            if (showRecipeList) {
                lineY += SECTION_GAP + 4;

                RecipeConfig cfg = ((IRecipeModule) module.component()).getRecipeConfig();
                List<RecipeSlot> slots = cfg != null ? cfg.slots()
                    .toList() : List.of();

                int recipeListX = x + CONTENT_PADDING;
                lineY = RECIPE_LIST_Y;

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
                        lineY += RECIPE_ROW_H;
                    }
                }
            }
        } else {
            showRecipeList = false;
        }
    }

    private static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    private void drawHammerConfig(ModuleInstance module, ModuleHammer hammer, int x, int y, int width) {
        int panelX = x + CONTENT_PADDING;
        int panelW = width - CONTENT_PADDING * 2;
        BorderedRect.draw(
            panelX,
            y,
            panelW,
            72,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());

        int lineY = y + 6;
        HammerVariant variant = hammer.variant();
        ModuleTier tier = module.tier();
        int cooldown = ModuleHammer.cooldownTicks(variant, tier);
        int chargeTicks = ModuleHammer.chargeTicks(variant, tier);
        long shotEnergy = ModuleHammer.shotEnergyEu(variant);
        long chargeRate = ModuleHammer.chargeRateEuPerTick(variant, tier);
        lineY = drawLine(
            "Hammer config",
            panelX + 6,
            lineY,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY = drawLine(
            "Shot: " + formatEu(shotEnergy) + " EU  Rate: " + formatEu(chargeRate) + " EU/t",
            panelX + 6,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = drawLine(
            "Cooldown: " + (cooldown / 20) + "s  Charge: " + (chargeTicks / 20) + "s",
            panelX + 6,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        int barX = panelX + 6;
        int barY = lineY + 2;
        int barW = panelW - 12;
        int barH = 8;
        int chargeProgress = Math.min(Math.max(module.ticks(), 0), chargeTicks);
        int fillW = (int) ((long) barW * chargeProgress / chargeTicks);
        Gui.drawRect(barX, barY, barX + barW, barY + barH, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(barX, barY, barX + fillW, barY + barH, EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
    }

    private ButtonWidget<?> createPanelButton(Supplier<String> labelSupplier, BooleanSupplier enabledSupplier,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
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
        return selectedModule() instanceof SelectedModule selected && selected.module.component() instanceof ModuleHammer;
    }

    private boolean hasRecipeModuleSelected() {
        return selectedModule() instanceof SelectedModule selected && selected.module.component() instanceof IRecipeModule;
    }

    private void openHammerConfig() {
        if (!hasHammerSelected()) return;
        showHammerConfig = true;
        showRecipeList = false;
    }

    private void openRecipeInput() {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        if (!(selected.module.component() instanceof IRecipeModule)) return;
        RecipeInputScreen.open(map.assetId(), selected.moduleIndex, selected.module);
    }

    private boolean canUseHammerConfigButtons() {
        return showHammerConfig && hasHammerSelected();
    }

    private String hammerVariantLabel() {
        ModuleHammer hammer = selectedHammer();
        return hammer == null ? "Variant" : hammer.variant()
            .name();
    }

    private String hammerTierLabel() {
        return selectedModule() instanceof SelectedModule selected ? selected.module.tier()
            .name() : "Tier";
    }

    private void cycleHammerVariant() {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        if (!(selected.module.component() instanceof ModuleHammer hammer)) return;
        HammerVariant next = hammer.variant() == HammerVariant.BASE ? HammerVariant.BIG : HammerVariant.BASE;
        ModuleHammer.requireTier(next, selected.module.tier());
        CelestialClient.updateModuleConfig(
            map.assetId(),
            selected.moduleIndex,
            AssetModuleUpdatePacket.ConfigAction.SET_HAMMER_VARIANT,
            next);
    }

    private void cycleHammerTier() {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        if (!(selected.module.component() instanceof ModuleHammer hammer)) return;
        ModuleTier next = nextHammerTier(hammer.variant(), selected.module.tier());
        CelestialClient.updateModuleConfig(
            map.assetId(),
            selected.moduleIndex,
            AssetModuleUpdatePacket.ConfigAction.SET_TIER,
            next);
    }

    private boolean canRemoveRecipeSlot(int slotIndex) {
        if (!showRecipeList) return false;
        return slotIndex >= 0 && slotIndex < recipeSlotCount();
    }

    private void removeRecipeSlot(int slotIndex) {
        if (!(selectedModule() instanceof SelectedModule selected)) return;
        CelestialClient.updateModuleRecipeSlot(
            map.assetId(),
            selected.moduleIndex,
            AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT,
            (byte) slotIndex,
            null);
    }

    private int recipeSlotCount() {
        if (!(selectedModule() instanceof SelectedModule selected)) return 0;
        if (!(selected.module.component() instanceof IRecipeModule recipeModule)) return 0;
        RecipeConfig cfg = recipeModule.getRecipeConfig();
        return cfg == null ? 0 : cfg.slots()
            .toList()
            .size();
    }

    private @Nullable ModuleHammer selectedHammer() {
        return selectedModule() instanceof SelectedModule selected && selected.module.component() instanceof ModuleHammer hammer
            ? hammer
            : null;
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

    private static ModuleTier nextHammerTier(HammerVariant variant, ModuleTier current) {
        ModuleTier[] values = switch (variant) {
            case BASE -> new ModuleTier[] { ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV };
            case BIG -> new ModuleTier[] { ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV };
        };
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        throw new IllegalStateException("Hammer variant " + variant + " does not support tier " + current);
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
