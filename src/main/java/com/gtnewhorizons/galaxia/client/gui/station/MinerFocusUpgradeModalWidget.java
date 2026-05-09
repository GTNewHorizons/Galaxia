package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Map;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class MinerFocusUpgradeModalWidget extends ParentWidget<MinerFocusUpgradeModalWidget> {

    static final int WIDTH = 340;
    static final int HEIGHT = 204;

    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + 12;
    private static final int TIER_BUTTON_Y = BODY_TOP + 50;
    private static final int TIER_BUTTON_WIDTH = 42;
    private static final int TIER_BUTTON_HEIGHT = 18;
    private static final int TIER_BUTTON_GAP = 8;
    private static final int TIER_BUTTON_X = ModuleConfigModalSupport.PANEL_PADDING;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONFIRM_BUTTON_WIDTH = 72;
    private static final int BACK_BUTTON_WIDTH = 54;
    private static final int FOOTER_Y = HEIGHT - 30;
    private static final int BODY_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    MinerFocusUpgradeModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        MinerFocusTier[] tiers = { MinerFocusTier.I, MinerFocusTier.II, MinerFocusTier.III };
        for (int i = 0; i < tiers.length; i++) {
            MinerFocusTier tier = tiers[i];
            child(
                ModuleConfigModalSupport.button(
                    () -> canSelectTier(tier),
                    tier.name(),
                    () -> controller.setMinerFocusUpgradeTier(tier))
                    .pos(TIER_BUTTON_X + i * (TIER_BUTTON_WIDTH + TIER_BUTTON_GAP), TIER_BUTTON_Y)
                    .size(TIER_BUTTON_WIDTH, TIER_BUTTON_HEIGHT));
        }
        child(
            ModuleConfigModalSupport.button(this::canConfirm, "Confirm", this::confirm)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(CONFIRM_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(() -> controller.isMinerFocusUpgradeOpen(), "Back", controller::close)
                .pos(WIDTH - ModuleConfigModalSupport.PANEL_PADDING - BACK_BUTTON_WIDTH, FOOTER_Y)
                .size(BACK_BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isMinerFocusUpgradeOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleMiner miner)) {
            ModuleConfigModalSupport.drawLine(
                "No miner selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        int lineY = BODY_TOP;
        lineY = ModuleConfigModalSupport.drawLine(
            "Current tier: " + focusTierLabel(miner.focusTier()),
            ModuleConfigModalSupport.PANEL_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        MinerFocusTier targetTier = controller.minerFocusUpgradeTier();
        lineY = ModuleConfigModalSupport.drawLine(
            "Target tier: " + focusTierLabel(targetTier),
            ModuleConfigModalSupport.PANEL_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY += 3;
        lineY = ModuleConfigModalSupport.drawLine(
            "Effect: focus bonus " + miner.focusTier()
                .bonusPercent()
                + "% -> "
                + targetTier.bonusPercent()
                + "%",
            ModuleConfigModalSupport.PANEL_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY += TIER_BUTTON_HEIGHT + 12;
        if (MinerFocusUiModel.hasActiveOperation(module)) {
            ModuleConfigModalSupport.drawLine(
                "Active build in progress",
                ModuleConfigModalSupport.PANEL_PADDING,
                lineY,
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            return;
        }
        if (!MinerFocusUiModel.canPlanTier(module, targetTier)) {
            ModuleConfigModalSupport.drawLine(
                miner.focusTier() == MinerFocusTier.III ? "Max focus tier installed" : "No upgrade selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                lineY,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        drawCost(module, lineY);
    }

    private void confirm() {
        if (!canConfirm()) return;
        CelestialClient.planMinerFocusTier(assetId, controller.moduleIndex(), controller.minerFocusUpgradeTier());
        controller.close();
    }

    private boolean canConfirm() {
        return MinerFocusUiModel.canPlanTier(selectedModule(), controller.minerFocusUpgradeTier());
    }

    private boolean canSelectTier(MinerFocusTier tier) {
        ModuleInstance module = selectedModule();
        ModuleMiner miner = module != null && module.component() instanceof ModuleMiner selectedMiner ? selectedMiner
            : null;
        return controller.isMinerFocusUpgradeOpen() && module != null
            && miner != null
            && !MinerFocusUiModel.hasActiveOperation(module)
            && tier != MinerFocusTier.NONE
            && tier.ordinal() > miner.focusTier()
                .ordinal();
    }

    private void drawCost(ModuleInstance module, int lineY) {
        ModuleTierData sourceData = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        lineY = ModuleConfigModalSupport.drawLine(
            "Build: " + sourceData.buildTicks()
                + " ticks ("
                + sourceData.buildTicks() / 20
                + "s)",
            ModuleConfigModalSupport.PANEL_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport.drawLine(
            "Cost:",
            ModuleConfigModalSupport.PANEL_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(sourceData.constructionCost());
        if (cost.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "None",
                ModuleConfigModalSupport.PANEL_PADDING,
                lineY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            return;
        }
        int shown = 0;
        for (Map.Entry<ItemStackWrapper, Long> entry : cost.entrySet()) {
            if (shown >= 3) {
                ModuleConfigModalSupport.drawLine(
                    "...",
                    ModuleConfigModalSupport.PANEL_PADDING,
                    lineY,
                    EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                break;
            }
            lineY = ModuleConfigModalSupport.drawTrimmedLine(
                entry.getValue() + "x "
                    + entry.getKey()
                        .toStack(1)
                        .getDisplayName(),
                ModuleConfigModalSupport.PANEL_PADDING,
                lineY,
                BODY_WIDTH,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            shown++;
        }
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Miner Upgrade" : ModuleConfigModalSupport.moduleTitle(module, "Upgrade");
    }

    private static String focusTierLabel(MinerFocusTier tier) {
        return tier == MinerFocusTier.NONE ? "None" : tier.name();
    }
}
