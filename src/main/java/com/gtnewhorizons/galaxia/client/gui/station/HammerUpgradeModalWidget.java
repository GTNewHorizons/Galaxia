package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

final class HammerUpgradeModalWidget extends ParentWidget<HammerUpgradeModalWidget> {

    static final int WIDTH = 390;
    static final int HEIGHT = 260;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_TOP = HEIGHT - 58;
    private static final int FOOTER_TOP = HEIGHT - 30;
    private static final int TARGET_BUTTON_WIDTH = 64;
    private static final int TIER_BUTTON_WIDTH = 50;
    private static final int RESERVE_BUTTON_WIDTH = 84;
    private static final int VOID_BUTTON_WIDTH = 98;
    private static final int CONFIRM_BUTTON_WIDTH = 72;
    private static final int BACK_BUTTON_WIDTH = 54;
    private static final int COLUMN_GAP = 4;
    private static final int BODY_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    HammerUpgradeModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        child(
            ModuleConfigModalSupport
                .button(this::canUseControls, "Variant", "Switch target hammer variant", this::cycleVariant)
                .pos(x, BUTTON_TOP)
                .size(TARGET_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += TARGET_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport.button(() -> canShiftTier(-1), "Tier -", "Lower target tier", () -> shiftTier(-1))
                .pos(x, BUTTON_TOP)
                .size(TIER_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += TIER_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport.button(() -> canShiftTier(1), "Tier +", "Raise target tier", () -> shiftTier(1))
                .pos(x, BUTTON_TOP)
                .size(TIER_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += TIER_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport
                .button(
                    this::canUseControls,
                    this::reserveLabel,
                    "Store delivered build items in this module until all costs are ready",
                    controller::toggleHammerUpgradeReserveItems)
                .pos(x, BUTTON_TOP)
                .size(RESERVE_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += RESERVE_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport
                .button(
                    this::canUseControls,
                    this::voidLabel,
                    "Delete the 80% refund from the replaced module",
                    controller::toggleHammerUpgradeVoidRefund)
                .pos(x, BUTTON_TOP)
                .size(VOID_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfirm, "Confirm", this::confirm)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_TOP)
                .size(CONFIRM_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canUseControls, "Back", this::back)
                .pos(WIDTH - ModuleConfigModalSupport.PANEL_PADDING - BACK_BUTTON_WIDTH, FOOTER_TOP)
                .size(BACK_BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isHammerUpgradeOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) {
            ModuleConfigModalSupport.drawLine(
                "No hammer selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        drawUpgradePlan(module, hammer);
    }

    private void drawUpgradePlan(ModuleInstance module, ModuleHammer hammer) {
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        int lineY = BODY_TOP;
        HammerVariant targetVariant = controller.hammerUpgradeVariant();
        ModuleTier targetTier = controller.hammerUpgradeTier();
        lineY = ModuleConfigModalSupport.drawLine(
            "Current: " + hammer.variant()
                .name()
                + " "
                + module.tier()
                    .name(),
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport.drawLine(
            "Target: " + targetVariant.name() + " " + targetTier.name(),
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY += 3;
        lineY = ModuleConfigModalSupport.drawLine("Variants", x, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY = drawVariantLine(lineY, HammerVariant.BASE);
        lineY = drawVariantLine(lineY, HammerVariant.BIG);
        lineY += 3;
        lineY = ModuleConfigModalSupport
            .drawLine("Tiers for " + targetVariant.name(), x, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY = drawTierLine(lineY, targetVariant, targetTier);
        lineY += 4;
        if (!targetChanged(module, hammer)) {
            ModuleConfigModalSupport.drawLine("No change", x, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        lineY = ModuleConfigModalSupport.drawLine("Cost:", x, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        ModuleTierData targetData = FacilityModuleRegistry.get(module.kind())
            .getTierData(controller.hammerUpgradeTier());
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(targetData.constructionCost());
        if (cost.isEmpty()) {
            ModuleConfigModalSupport.drawLine("None", x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            return;
        }
        int shown = 0;
        for (Map.Entry<ItemStackWrapper, Long> entry : cost.entrySet()) {
            if (shown >= 3) {
                ModuleConfigModalSupport.drawLine("...", x, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                break;
            }
            lineY = ModuleConfigModalSupport.drawTrimmedLine(
                entry.getValue() + "x "
                    + entry.getKey()
                        .toStack(1)
                        .getDisplayName(),
                x,
                lineY,
                BODY_WIDTH,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            shown++;
        }
    }

    private void cycleVariant() {
        HammerVariant next = controller.hammerUpgradeVariant() == HammerVariant.BASE ? HammerVariant.BIG
            : HammerVariant.BASE;
        controller.setHammerUpgradeVariant(next);
    }

    private void shiftTier(int delta) {
        HammerVariant variant = controller.hammerUpgradeVariant();
        ModuleTier current = controller.hammerUpgradeTier();
        List<ModuleTier> supported = supportedTiers(variant);
        int currentSupportedIndex = supported.indexOf(current);
        if (currentSupportedIndex < 0 || supported.isEmpty()) {
            throw new IllegalStateException("Hammer upgrade target tier is invalid: " + variant + "/" + current);
        }
        int nextSupportedIndex = Math.max(0, Math.min(supported.size() - 1, currentSupportedIndex + delta));
        controller.setHammerUpgradeTier(supported.get(nextSupportedIndex));
    }

    private String reserveLabel() {
        return controller.hammerUpgradeReserveItems() ? "Reserve On" : "Reserve Off";
    }

    private String voidLabel() {
        return controller.hammerUpgradeVoidRefund() ? "Void refund On" : "Void refund Off";
    }

    private boolean canUseControls() {
        return controller.isHammerUpgradeOpen() && selectedModule() != null && !hasActiveOperation();
    }

    private boolean canConfirm() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer) || hasActiveOperation())
            return false;
        return targetChanged(module, hammer);
    }

    private boolean canShiftTier(int delta) {
        if (!canUseControls()) return false;
        HammerVariant variant = controller.hammerUpgradeVariant();
        List<ModuleTier> supported = supportedTiers(variant);
        int index = supported.indexOf(controller.hammerUpgradeTier());
        return index >= 0 && index + delta >= 0 && index + delta < supported.size();
    }

    private boolean hasActiveOperation() {
        ModuleInstance module = selectedModule();
        return module != null && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    private void confirm() {
        if (!canConfirm()) return;
        CelestialClient.planHammerUpgrade(
            assetId,
            controller.moduleIndex(),
            controller.hammerUpgradeVariant(),
            controller.hammerUpgradeTier(),
            controller.hammerUpgradeReserveItems(),
            controller.hammerUpgradeVoidRefund());
        controller.close();
    }

    private void back() {
        int moduleIndex = controller.moduleIndex();
        controller.openHammer(moduleIndex);
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Hammer Upgrade" : ModuleConfigModalSupport.moduleTitle(module, "Upgrade");
    }

    private int drawVariantLine(int lineY, HammerVariant variant) {
        return ModuleConfigModalSupport.drawTrimmedLine(
            variant.name() + "  Shot " + ModuleConfigModalSupport.formatEu(variant.shotEnergyEu()) + " EU",
            ModuleConfigModalSupport.PANEL_PADDING + 8,
            lineY,
            BODY_WIDTH,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private int drawTierLine(int lineY, HammerVariant variant, ModuleTier selectedTier) {
        for (ModuleTier tier : supportedTiers(variant)) {
            int color = tier == selectedTier ? EnumColors.MAP_COLOR_TEXT_SECTION.getColor()
                : EnumColors.MAP_COLOR_TEXT_BODY.getColor();
            ModuleTierData data = FacilityModuleRegistry.get(FacilityModuleKind.HAMMER)
                .getTierData(tier);
            int cooldown = data.variantCooldowns() != null && data.variantCooldowns()
                .containsKey(variant.name()) ? data.variantCooldowns()
                    .get(variant.name()) : data.cooldownTicks();
            int chargeTicks = Math.max(1, cooldown - 20);
            long chargeRate = Math.ceilDiv(variant.shotEnergyEu(), chargeTicks);
            lineY = ModuleConfigModalSupport.drawTrimmedLine(
                tier.name() + "  Cooldown "
                    + (cooldown / 20)
                    + "s  Rate "
                    + ModuleConfigModalSupport.formatEu(chargeRate)
                    + " EU/t",
                ModuleConfigModalSupport.PANEL_PADDING + 8,
                lineY,
                BODY_WIDTH,
                color);
        }
        return lineY;
    }

    private boolean targetChanged(ModuleInstance module, ModuleHammer hammer) {
        return hammer.variant() != controller.hammerUpgradeVariant() || module.tier() != controller.hammerUpgradeTier();
    }

    private static List<ModuleTier> supportedTiers(HammerVariant variant) {
        List<ModuleTier> supported = new ArrayList<>();
        for (ModuleTier tier : ModuleTier.values()) {
            if (ModuleHammer.supportsTier(variant, tier)) supported.add(tier);
        }
        return supported;
    }
}
