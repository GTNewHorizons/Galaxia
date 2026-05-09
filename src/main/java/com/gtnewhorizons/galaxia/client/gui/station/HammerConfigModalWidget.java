package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

final class HammerConfigModalWidget extends ParentWidget<HammerConfigModalWidget> {

    static final int WIDTH = 270;
    static final int HEIGHT = 154;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int ITEMS_BUTTON_WIDTH = 54;
    private static final int CLOSE_BUTTON_X = WIDTH - 62;
    private static final int CLOSE_BUTTON_WIDTH = 54;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    HammerConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(controller::isHammerOpen, "Items", this::openLogistics)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
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
        if (module.operationOrNull() != null) {
            ModuleConfigModalSupport.drawLine(
                operationLabel(module),
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        }
    }

    private void openLogistics() {
        if (selectedModule() == null) return;
        controller.openLogistics(controller.moduleIndex());
    }

    private boolean hasCancellableOperation() {
        ModuleInstance module = selectedModule();
        return controller.isHammerOpen() && module != null
            && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    private String operationLabel(ModuleInstance module) {
        if (hasCancellableOperation() && controller.isModuleOperationCancelArmed()) {
            return "Click Confirm to cancel operation";
        }
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
