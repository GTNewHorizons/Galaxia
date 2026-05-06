package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

final class ModuleConfigModalSupport {

    static final int HEADER_HEIGHT = 24;
    static final int PANEL_PADDING = 8;

    private ModuleConfigModalSupport() {}

    static void drawFrame(String title, int width, int height) {
        net.minecraft.client.gui.Gui.drawRect(0, 0, width, height, EnumColors.MAP_COLOR_MODAL_BG.getColor());
        net.minecraft.client.gui.Gui.drawRect(0, 0, width, HEADER_HEIGHT, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
        BorderedRect.draw(0, 0, width, height, 0x00000000, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
        drawLine(title, PANEL_PADDING, PANEL_PADDING, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    static ButtonWidget<?> button(String label, Runnable onClick) {
        return button(() -> true, label, onClick);
    }

    static ButtonWidget<?> button(BooleanSupplier enabledSupplier, String label, Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean()) return;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String trimmed = fr.trimStringToWidth(label, w - 4);
                int textW = fr.getStringWidth(trimmed);
                fr.drawStringWithShadow(
                    trimmed,
                    x + (w - textW) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            });
    }

    static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    static @Nullable AutomatedFacility facility(CelestialAsset.ID assetId) {
        return assetId != null && CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility
            : null;
    }

    static @Nullable ModuleInstance module(CelestialAsset.ID assetId, int moduleIndex) {
        AutomatedFacility facility = facility(assetId);
        if (facility == null || moduleIndex < 0
            || moduleIndex >= facility.modules()
                .size()) {
            return null;
        }
        return facility.modules()
            .get(moduleIndex);
    }

    static String formatEu(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    static IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private static void drawButtonBackground(int x, int y, int w, int h, boolean enabled, boolean hovered) {
        if (!enabled) return;
        BorderedRect.draw(
            x,
            y,
            w,
            h,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
    }
}
