package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

public class StationSettingsWidget extends ParentWidget<StationSettingsWidget> {

    private static final int SETTINGS_PAD = 8;
    private static final int SETTINGS_SCREEN_PAD = 5;
    private static final int LABEL_ROW_HEIGHT = 10;
    private static final int FIELD_WIDTH = 80;
    private static final int FIELD_HEIGHT = 16;

    private final Flow settings;
    private final Flow column;

    public StationSettingsWidget(IKey nameKey, IPanelHandler handler) {
        full();
        padding(SETTINGS_PAD);

        settings = Flow.col()
            .name("help")
            .padding(SETTINGS_SCREEN_PAD)
            .childPadding(SETTINGS_PAD / 2)
            .mainAxisAlignment(Alignment.MainAxis.CENTER);

        column = Flow.col()
            .full()
            .child(
                new StationHeaderWidget(nameKey).button(StationButtonWidget.closeButton(handler::closePanel))
                    .fullWidth())
            .child(
                new ScreenWidget().child(settings)
                    .fullWidth()
                    .expanded());

        child(column);
    }

    /** Appends a fixed-size footer row (e.g. the reset button) below the settings screen. */
    public StationSettingsWidget footer(IWidget widget) {
        column.child(widget);
        return getThis();
    }

    public StationSettingsWidget setting(IKey key, IWidget widget) {
        settings.child(
            key.asWidget()
                .height(LABEL_ROW_HEIGHT));
        settings.child(widget);

        return getThis();
    }

    public StationSettingsWidget numberSetting(IKey key, IntSyncValue value, int min, int max) {
        return setting(
            key,
            new TextFieldWidget().value(value)
                .size(FIELD_WIDTH, FIELD_HEIGHT)
                .numbersInt(min, max)
                .formatAsInteger(true)
                .setTextAlignment(Alignment.Center)
                .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.range", min, max))));
    }

}
