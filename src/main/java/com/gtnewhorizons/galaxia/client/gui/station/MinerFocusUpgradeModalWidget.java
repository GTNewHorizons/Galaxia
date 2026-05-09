package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class MinerFocusUpgradeModalWidget extends ParentWidget<MinerFocusUpgradeModalWidget> {

    static final int WIDTH = 260;
    static final int HEIGHT = 128;

    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + 12;
    private static final int TIER_BUTTON_Y = BODY_TOP + 38;
    private static final int TIER_BUTTON_WIDTH = 42;
    private static final int TIER_BUTTON_HEIGHT = 18;
    private static final int TIER_BUTTON_GAP = 8;
    private static final int TIER_BUTTON_X = ModuleConfigModalSupport.PANEL_PADDING;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int FOOTER_Y = HEIGHT - 28;

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
                    () -> MinerFocusUiModel.canPlanTier(selectedModule(), tier),
                    tier.name(),
                    () -> planFocusTier(tier))
                    .pos(TIER_BUTTON_X + i * (TIER_BUTTON_WIDTH + TIER_BUTTON_GAP), TIER_BUTTON_Y)
                    .size(TIER_BUTTON_WIDTH, TIER_BUTTON_HEIGHT));
        }
        child(
            ModuleConfigModalSupport.button(() -> controller.isMinerFocusUpgradeOpen(), "Close", controller::close)
                .pos(WIDTH - ModuleConfigModalSupport.PANEL_PADDING - CLOSE_BUTTON_WIDTH, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT));
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
        ModuleConfigModalSupport.drawLine(
            "Current tier: " + focusTierLabel(miner.focusTier()),
            ModuleConfigModalSupport.PANEL_PADDING,
            BODY_TOP,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawLine(
            MinerFocusUiModel.hasActiveOperation(module) ? "Active build in progress" : "Choose target focus tier",
            ModuleConfigModalSupport.PANEL_PADDING,
            BODY_TOP + 16,
            MinerFocusUiModel.hasActiveOperation(module) ? EnumColors.MAP_COLOR_TEXT_WARNING.getColor()
                : EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void planFocusTier(MinerFocusTier tier) {
        if (!MinerFocusUiModel.canPlanTier(selectedModule(), tier)) return;
        CelestialClient.planMinerFocusTier(assetId, controller.moduleIndex(), tier);
        controller.close();
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Miner Focus Upgrade" : ModuleConfigModalSupport.moduleTitle(module, "Focus Upgrade");
    }

    private static String focusTierLabel(MinerFocusTier tier) {
        return tier == MinerFocusTier.NONE ? "None" : tier.name();
    }
}
