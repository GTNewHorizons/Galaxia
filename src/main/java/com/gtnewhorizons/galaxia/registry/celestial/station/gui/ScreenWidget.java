package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumColors;

public class ScreenWidget extends ParentWidget<ScreenWidget> {

    private static final int BRACKET_SIZE = 9;
    private static final int BRACKET_THICK = 2;

    public ScreenWidget() {
        background(bg());

        child(cornerBracket(false, false).pos(2, 2));
        child(
            cornerBracket(true, false).right(2)
                .top(2));
        child(
            cornerBracket(false, true).left(2)
                .bottom(2));
        child(
            cornerBracket(true, true).right(2)
                .bottom(2));
    }

    public static IDrawable[] bg() {
        return new IDrawable[] { new Rectangle().color(EnumColors.AIRLOCK_SCREEN_BG.getColor()),
            new Rectangle().hollow(1)
                .color(EnumColors.AIRLOCK_SCREEN_BORDER.getColor()) };
    }

    /**
     * Single L-shaped corner bracket as two rectangle strips. Anchor it with {@code left/right} and {@code top/bottom}
     * on the returned widget.
     */
    static ParentWidget<?> cornerBracket(boolean right, boolean bottom) {
        int vx = right ? BRACKET_SIZE - BRACKET_THICK : 0;
        int hy = bottom ? BRACKET_SIZE - BRACKET_THICK : 0;
        Rectangle strip = new Rectangle().color(EnumColors.AIRLOCK_SCREEN_BRACKET.getColor());
        return new ParentWidget<>().size(BRACKET_SIZE, BRACKET_SIZE)
            .child(
                new Widget<>().pos(vx, 0)
                    .size(BRACKET_THICK, BRACKET_SIZE)
                    .background(strip))
            .child(
                new Widget<>().pos(0, hy)
                    .size(BRACKET_SIZE, BRACKET_THICK)
                    .background(strip));
    }
}
