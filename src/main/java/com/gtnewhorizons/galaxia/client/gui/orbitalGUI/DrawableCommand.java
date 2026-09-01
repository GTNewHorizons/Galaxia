package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;

@FunctionalInterface
public interface DrawableCommand {

    void draw(GuiContext ctx, int x, int y, int w, int h);

    static IDrawable asDrawable(DrawableCommand command) {
        return (ctx, x, y, w, h, theme) -> command.draw(ctx, x, y, w, h);
    }
}
