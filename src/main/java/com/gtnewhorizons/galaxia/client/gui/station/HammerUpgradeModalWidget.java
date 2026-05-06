package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Map;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationTargetSpec;

final class HammerUpgradeModalWidget extends ParentWidget<HammerUpgradeModalWidget> {

    static final int WIDTH = 320;
    static final int HEIGHT = 210;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_TOP = HEIGHT - 58;
    private static final int FOOTER_TOP = HEIGHT - 30;
    private static final int TARGET_BUTTON_WIDTH = 58;
    private static final int TIER_BUTTON_WIDTH = 42;
    private static final int RESERVE_BUTTON_WIDTH = 62;
    private static final int VOID_BUTTON_WIDTH = 54;
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
            ModuleConfigModalSupport.button(this::canUseControls, "Variant", this::cycleVariant)
                .pos(x, BUTTON_TOP)
                .size(TARGET_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += TARGET_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport.button(this::canUseControls, "Tier -", () -> shiftTier(-1))
                .pos(x, BUTTON_TOP)
                .size(TIER_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += TIER_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport.button(this::canUseControls, "Tier +", () -> shiftTier(1))
                .pos(x, BUTTON_TOP)
                .size(TIER_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += TIER_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport
                .button(this::canUseControls, this::reserveLabel, controller::toggleHammerUpgradeReserveItems)
                .pos(x, BUTTON_TOP)
                .size(RESERVE_BUTTON_WIDTH, BUTTON_HEIGHT));
        x += RESERVE_BUTTON_WIDTH + COLUMN_GAP;
        child(
            ModuleConfigModalSupport
                .button(this::canUseControls, this::voidLabel, controller::toggleHammerUpgradeVoidRefund)
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
        ModuleConfigModalSupport.drawFrame("Hammer upgrade", WIDTH, HEIGHT);
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
        lineY = ModuleConfigModalSupport.drawLine(
            "Shot: " + ModuleConfigModalSupport.formatEu(ModuleHammer.shotEnergyEu(targetVariant))
                + " EU  Rate: "
                + ModuleConfigModalSupport.formatEu(ModuleHammer.chargeRateEuPerTick(targetVariant, targetTier))
                + " EU/t",
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport.drawLine(
            "Cooldown: " + (ModuleHammer.cooldownTicks(targetVariant, targetTier) / 20)
                + "s  Charge: "
                + (ModuleHammer.chargeTicks(targetVariant, targetTier) / 20)
                + "s",
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY += 4;
        lineY = ModuleConfigModalSupport.drawLine("Cost:", x, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        Map<ItemStack, Long> cost = FacilityModuleRegistry.get(module.kind())
            .operationDefinition(ModuleOperationKind.UPGRADE_REBUILD)
            .materialCost(targetSpec(module, hammer));
        if (cost.isEmpty()) {
            ModuleConfigModalSupport.drawLine("None", x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            return;
        }
        int shown = 0;
        for (Map.Entry<ItemStack, Long> entry : cost.entrySet()) {
            if (shown >= 3) {
                ModuleConfigModalSupport.drawLine("...", x, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                break;
            }
            lineY = ModuleConfigModalSupport.drawTrimmedLine(
                entry.getValue() + "x "
                    + entry.getKey()
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
        ModuleTier[] values = ModuleTier.values();
        int currentSupportedIndex = -1;
        int supportedCount = 0;
        for (ModuleTier value : values) {
            if (!ModuleHammer.supportsTier(variant, value)) continue;
            if (value == current) currentSupportedIndex = supportedCount;
            supportedCount++;
        }
        if (currentSupportedIndex < 0 || supportedCount == 0) {
            throw new IllegalStateException("Hammer upgrade target tier is invalid: " + variant + "/" + current);
        }
        int nextSupportedIndex = Math.floorMod(currentSupportedIndex + delta, supportedCount);
        int seen = 0;
        for (ModuleTier value : values) {
            if (!ModuleHammer.supportsTier(variant, value)) continue;
            if (seen == nextSupportedIndex) {
                controller.setHammerUpgradeTier(value);
                return;
            }
            seen++;
        }
        throw new IllegalStateException("Failed to resolve next hammer tier for " + variant + "/" + current);
    }

    private String reserveLabel() {
        return controller.hammerUpgradeReserveItems() ? "Res On" : "Res Off";
    }

    private String voidLabel() {
        return controller.hammerUpgradeVoidRefund() ? "Void On" : "Void Off";
    }

    private boolean canUseControls() {
        return controller.isHammerUpgradeOpen() && selectedModule() != null && !hasActiveOperation();
    }

    private boolean canConfirm() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer) || hasActiveOperation())
            return false;
        return hammer.variant() != controller.hammerUpgradeVariant() || module.tier() != controller.hammerUpgradeTier();
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

    private ModuleOperationTargetSpec targetSpec(ModuleInstance module, ModuleHammer hammer) {
        return new ModuleOperationTargetSpec(
            ModuleOperationKind.UPGRADE_REBUILD,
            module.kind(),
            module.tier(),
            hammer.variant()
                .name(),
            module.kind(),
            controller.hammerUpgradeTier(),
            controller.hammerUpgradeVariant()
                .name());
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleIndex());
    }
}
