package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumColors;

public class StationHeaderWidget extends ParentWidget<StationHeaderWidget> {

    private final Flow buttonHolder;

    public static final int HEADER_INSET = 4;
    private static final int HEADER_HEIGHT = 18;

    public StationHeaderWidget(IKey key) {
        this(key, new Rectangle().color(EnumColors.AIRLOCK_SCREEN_BORDER.getColor()));
    }

    public StationHeaderWidget(IKey key, IDrawable status) {
        super();

        margin(0, HEADER_INSET);

        background(
            new Rectangle().verticalGradient(
                EnumColors.STATION_HEADER_TOP.getColor(),
                EnumColors.STATION_HEADER_BOTTOM.getColor()));

        height(HEADER_HEIGHT);

        buttonHolder = Flow.row()
            .full()
            .mainAxisAlignment(Alignment.MainAxis.END);

        child(
            Flow.col()
                .full()
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .child(
                    new Widget<>().widthRel(1f)
                        .height(1)
                        .background(new Rectangle().color(EnumColors.STATION_HEADER_HIGHLIGHT.getColor())))
                .child(
                    Flow.row()
                        .full()
                        .padding(2, 0)
                        .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                        .child(key.asWidget())
                        .child(buttonHolder))
                .child(
                    new Widget<>().widthRel(1f)
                        .height(1)
                        .background(status)));
    }

    public StationHeaderWidget button(IWidget widget) {
        buttonHolder.child(widget);

        return getThis();
    }

}
