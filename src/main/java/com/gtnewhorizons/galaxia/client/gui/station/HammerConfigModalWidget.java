package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

final class HammerConfigModalWidget extends ParentWidget<HammerConfigModalWidget> {

    static final int WIDTH = 270;
    static final int HEIGHT = 154;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int BAR_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int UPGRADE_BUTTON_WIDTH = 70;
    private static final int ITEMS_BUTTON_X = 86;
    private static final int ITEMS_BUTTON_WIDTH = 54;
    private static final int CLOSE_BUTTON_X = WIDTH - 62;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int BAR_TOP_OFFSET = 2;
    private static final int BAR_HEIGHT = 8;
    private static final int OPERATION_TEXT_OFFSET = 5;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    HammerConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport
                .button(this::canUsePrimaryButton, this::primaryButtonLabel, this::primaryButtonAction)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(UPGRADE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isHammerOpen, "Items", this::openLogistics)
                .pos(ITEMS_BUTTON_X, FOOTER_Y)
                .size(ITEMS_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isHammerOpen, "Close", controller::close)
                .pos(CLOSE_BUTTON_X, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isHammerOpen()) return;
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
        drawSummary(module, hammer);
    }

    private void drawSummary(ModuleInstance module, ModuleHammer hammer) {
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        int y = BODY_TOP;
        HammerVariant variant = hammer.variant();
        ModuleTier tier = module.tier();
        int cooldown = module.cooldownTicks();
        int chargeTicks = Math.max(1, cooldown - 20);
        long shotEnergy = variant.shotEnergyEu();
        long chargeRate = Math.ceilDiv(shotEnergy, chargeTicks);

        int lineY = y;
        lineY = ModuleConfigModalSupport.drawLine(
            "Variant: " + variant.name() + "  Tier: " + tier.name(),
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport.drawLine(
            "Shot: " + ModuleConfigModalSupport.formatEu(shotEnergy)
                + " EU  Rate: "
                + ModuleConfigModalSupport.formatEu(chargeRate)
                + " EU/t",
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport
            .drawLine("Charge: " + (chargeTicks / 20) + "s", x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        int barY = lineY + BAR_TOP_OFFSET;
        int chargeProgress = Math.min(Math.max(module.ticks(), 0), chargeTicks);
        int fillW = (int) ((long) BAR_WIDTH * chargeProgress / chargeTicks);
        Gui.drawRect(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(
            x,
            barY,
            x + fillW,
            barY + BAR_HEIGHT,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
        if (module.operationOrNull() != null) {
            int operationY = barY + BAR_HEIGHT + OPERATION_TEXT_OFFSET;
            ModuleConfigModalSupport
                .drawLine(operationLabel(module), x, operationY, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        }
    }

    private void openUpgrade() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;
        controller.openHammerUpgrade(controller.moduleIndex(), hammer.variant(), module.tier());
    }

    private void openLogistics() {
        if (selectedModule() == null) return;
        controller.openLogistics(controller.moduleIndex());
    }

    private void cancelOperation() {
        if (!hasCancellableOperation()) return;
        CelestialClient.cancelModuleOperation(assetId, controller.moduleIndex());
    }

    private boolean canUsePrimaryButton() {
        return canOpenUpgrade() || hasCancellableOperation();
    }

    private String primaryButtonLabel() {
        return hasCancellableOperation() ? "Cancel" : "Upgrade";
    }

    private void primaryButtonAction() {
        if (hasCancellableOperation()) {
            cancelOperation();
        } else {
            openUpgrade();
        }
    }

    private boolean canOpenUpgrade() {
        ModuleInstance module = selectedModule();
        return controller.isHammerOpen() && module != null
            && (module.operationOrNull() == null || module.operationOrNull()
                .phase()
                .isTerminal());
    }

    private boolean hasCancellableOperation() {
        ModuleInstance module = selectedModule();
        return controller.isHammerOpen() && module != null
            && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    private static String operationLabel(ModuleInstance module) {
        return switch (module.operationOrNull()
            .phase()) {
            case WAITING_FOR_MATERIALS -> "Module is waiting for materials";
            case BUILDING -> "Module is upgrading";
            case REFUNDING -> "Module is refunding";
            case COMPLETE -> "Upgrade complete";
            case CANCELLED -> "Upgrade cancelled";
        };
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Hammer Configuration" : ModuleConfigModalSupport.moduleTitle(module, "Configuration");
    }
}
