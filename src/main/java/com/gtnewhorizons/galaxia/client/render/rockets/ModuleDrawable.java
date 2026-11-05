package com.gtnewhorizons.galaxia.client.render.rockets;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;

/**
 * ModularUI drawable showing a rocket module model, used for icons wherever a module is listed
 */
public class ModuleDrawable implements IDrawable {

    private final Supplier<IRocketPartDef> def;
    private final int padding;

    public ModuleDrawable(IRocketPartDef def, int padding) {
        this(() -> def, padding);
    }

    /**
     * @param def     Supplies the module to draw, may change between frames
     * @param padding Empty border kept around the model, in pixels
     */
    public ModuleDrawable(Supplier<IRocketPartDef> def, int padding) {
        this.def = def;
        this.padding = padding;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        IRocketPartDef current = def.get();
        if (current == null) return;
        ModuleModelRenderer.drawInGui(current, x + padding, y + padding, width - padding * 2, height - padding * 2, 1f);
    }
}
