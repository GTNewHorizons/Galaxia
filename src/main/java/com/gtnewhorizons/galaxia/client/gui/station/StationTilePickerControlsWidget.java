package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;

final class StationTilePickerControlsWidget extends ParentWidget<StationTilePickerControlsWidget> {

    static final int WIDTH = 204;
    static final int HEIGHT = 72;

    private static final int BUTTON_Y = 42;
    private static final int BUTTON_WIDTH = 76;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CANCEL_X = 10;
    private static final int CONFIRM_X = WIDTH - BUTTON_WIDTH - 10;

    private final StationTilePickerController controller;

    StationTilePickerControlsWidget(StationTilePickerController controller) {
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(controller::isActive, "Cancel", controller::cancel)
                .pos(CANCEL_X, BUTTON_Y)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::canConfirm, controller::confirmLabel, controller::confirm)
                .pos(CONFIRM_X, BUTTON_Y)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return !controller.isActive();
    }

    @Override
    public boolean canHover() {
        return controller.isActive();
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isActive()) return;
        BorderedRect.draw(
            0,
            0,
            WIDTH,
            HEIGHT,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());
        ModuleConfigModalSupport
            .drawTrimmedLine(controller.title(), 10, 9, WIDTH - 20, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
        ModuleConfigModalSupport
            .drawLine("Selected: " + controller.selectedCount(), 10, 24, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        if (controller.rotatesFootprint()) {
            ModuleConfigModalSupport.drawLine(
                "R: Anchor " + (controller.footprintRotation() + 1) + "/4",
                96,
                24,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
    }
}
