package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.screen.viewport.GuiContext;

@FunctionalInterface
public interface DrawableCommand {

    void draw(GuiContext ctx, int x, int y, int w, int h);
}
