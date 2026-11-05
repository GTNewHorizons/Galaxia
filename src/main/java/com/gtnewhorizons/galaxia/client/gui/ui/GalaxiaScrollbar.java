package com.gtnewhorizons.galaxia.client.gui.ui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.gtnewhorizons.galaxia.client.EnumTextures;

/**
 * Vertical scrollbar of the silo design: a narrow groove with a fixed handle wider than the groove riding on it
 */
public final class GalaxiaScrollbar extends VerticalScrollData {

    /** Width taken from the scrolled area, the handle's width */
    public static final int WIDTH = 12;
    private static final int TRACK_WIDTH = 6;
    private static final int HANDLE_HEIGHT = 15;

    public GalaxiaScrollbar() {
        super(false, WIDTH);
    }

    @Override
    public int getScrollBarLength(ScrollArea area) {
        return Math.min(HANDLE_HEIGHT, getFullVisibleSize(area));
    }

    @Override
    public void drawScrollbar(ScrollArea area, ModularGuiContext context, WidgetTheme widgetTheme, IDrawable texture) {
        int x = area.w() - WIDTH;
        int length = getScrollBarLength(area);
        boolean active = isDragging();
        (active ? EnumTextures.GUI_SCROLLBAR_TRACK_HOVERED : EnumTextures.GUI_SCROLLBAR_TRACK).getImage()
            .draw(context, x + (WIDTH - TRACK_WIDTH) / 2, 0, TRACK_WIDTH, area.height, widgetTheme);
        int y = getScrollBarStart(area, length, false);
        (active ? EnumTextures.GUI_SCROLLBAR_HANDLE_HOVERED : EnumTextures.GUI_SCROLLBAR_HANDLE).getImage()
            .draw(context, x, y, WIDTH, length, widgetTheme);
    }
}
