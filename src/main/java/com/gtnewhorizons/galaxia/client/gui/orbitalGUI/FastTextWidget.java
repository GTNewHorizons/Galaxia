package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/**
 * A highly optimized text widget that bypasses ModularUI's TextRenderer,
 * avoiding string splitting, line measuring, styling, and Area/Box allocations
 * when updated. Used for simple GUI texts that update frequently.
 */
public class FastTextWidget extends Widget<FastTextWidget> {

    private final Supplier<String> textSupplier;
    private int color = 0xFFFFFF;
    private boolean shadow = false;

    public FastTextWidget(String text) {
        this.textSupplier = () -> text;
    }

    public FastTextWidget(Supplier<String> textSupplier) {
        this.textSupplier = textSupplier;
    }

    public FastTextWidget color(int color) {
        this.color = color;
        return this;
    }

    public FastTextWidget shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        String text = textSupplier.get();
        if (text == null || text.isEmpty()) return;

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int x = getArea().getPadding()
            .getLeft();
        int y = getArea().getPadding()
            .getTop();

        if (shadow) {
            fr.drawStringWithShadow(text, x, y, color);
        } else {
            fr.drawString(text, x, y, color);
        }
    }

    @Override
    public int getDefaultHeight() {
        return Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
    }

    @Override
    public int getDefaultWidth() {
        String text = textSupplier.get();
        return text == null ? 0 : Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
    }
}
