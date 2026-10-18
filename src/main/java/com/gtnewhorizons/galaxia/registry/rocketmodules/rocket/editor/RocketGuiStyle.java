package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.EnumTextures;

/**
 * Shared look of the silo and assembler screens, so every popup uses the same frame, buttons and colors as the
 * silo background texture
 */
public final class RocketGuiStyle {

    public static final int TEXT = 0xFFD8DCEB;
    public static final int TEXT_DIM = 0xFF8D93AE;
    public static final int TEXT_TITLE = 0xFFFFFFFF;
    public static final int GOOD = 0xFF6FD39A;
    public static final int WARN = 0xFFE8C65A;
    public static final int BAD = 0xFFE36D6D;
    public static final int ACCENT = 0xFF7FA6E0;
    public static final int BAR_BACK = 0xFF27283B;
    public static final int BAR_EDGE = 0xFF54556B;

    private RocketGuiStyle() {}

    /** A popup panel with the silo frame */
    public static ModularPanel panel(String name, int width, int height) {
        return new ModularPanel(name).size(width, height)
            .background(EnumTextures.ROCKET_PANEL.getImage());
    }

    public static TextWidget<?> title(String langKey) {
        return IKey.lang(langKey)
            .color(TEXT_TITLE)
            .shadow(true)
            .asWidget();
    }

    public static TextWidget<?> label(Supplier<String> text, IntSupplier color, float scale) {
        return IKey.dynamic(text::get)
            .asWidget()
            .color(color)
            .scale(scale);
    }

    /** Flat button in the silo style, highlighted while hovered */
    public static ButtonWidget<?> button() {
        return new ButtonWidget<>().background(EnumTextures.ROCKET_BUTTON.getImage())
            .hoverBackground(EnumTextures.ROCKET_BUTTON_HOVERED.getImage());
    }

    /** Button with a text label */
    public static ButtonWidget<?> textButton(IKey text) {
        return button().overlay(
            text.color(TEXT)
                .shadow(true));
    }

    /**
     * Horizontal fill bar drawn in the silo palette
     *
     * @param fraction Fill level from 0 to 1
     * @param color    Fill color, ARGB
     *
     * @return A widget showing the bar
     */
    public static Widget<?> bar(DoubleSupplier fraction, IntSupplier color) {
        IDrawable drawable = (context, x, y, width, height, theme) -> {
            GuiDraw.drawRect(x, y, width, height, BAR_EDGE);
            GuiDraw.drawRect(x + 1, y + 1, width - 2, height - 2, BAR_BACK);
            int filled = (int) Math.round((width - 2) * Math.max(0, Math.min(1, fraction.getAsDouble())));
            if (filled > 0) GuiDraw.drawRect(x + 1, y + 1, filled, height - 2, color.getAsInt());
        };
        return new Widget<>().background(drawable);
    }

    /** Player inventory with slots in the silo palette instead of the light vanilla ones */
    public static SlotGroupWidget playerInventory() {
        return SlotGroupWidget.playerInventory((index, slot) -> slot.background(EnumTextures.ROCKET_SLOT.getImage()));
    }

    /** Formats a delta-v in m/s with a thousands separator */
    public static String deltaV(double metresPerSecond) {
        return String.format("%,d m/s", Math.round(metresPerSecond));
    }

    /** Formats a fluid amount, switching to buckets above 10 000 mB */
    public static String fluid(long millibuckets) {
        if (millibuckets >= 10_000) return String.format("%,.1f B", millibuckets / 1000.0);
        return String.format("%,d mB", millibuckets);
    }
}
