package com.gtnewhorizons.galaxia.client.gui.station;

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

final class StationCopyModuleMapButton extends ButtonWidget<StationCopyModuleMapButton> {

    private static final String LABEL = "Copy Module";
    private static final int BUTTON_TEXT_BASELINE_OFFSET = 1;

    private final @Nullable CelestialAsset.ID assetId;
    private final StationMapWidget map;
    private final StationEditModeController editModeController;
    private final boolean creativeBuildMode;

    StationCopyModuleMapButton(@Nullable CelestialAsset.ID assetId, StationMapWidget map,
        StationEditModeController editModeController, boolean creativeBuildMode) {
        this.assetId = assetId;
        this.map = map;
        this.editModeController = editModeController;
        this.creativeBuildMode = creativeBuildMode;
        background(drawable((ctx, x, y, w, h) -> drawButton(x, y, w, h, false)));
        hoverBackground(drawable((ctx, x, y, w, h) -> drawButton(x, y, w, h, true)));
        overlay(drawable((ctx, x, y, w, h) -> drawLabel(x, y, w, h)));
        onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            StationCopyModuleActionModel.Source source = source();
            if (source == null) return false;
            StationManagementScreen
                .openCopyBuildPicker(assetId, source.moduleIndex(), source.moduleId(), creativeBuildMode);
            return true;
        });
        setEnabledIf(w -> source() != null);
    }

    private void drawButton(int x, int y, int width, int height, boolean hovered) {
        if (source() == null) return;
        BorderedRect.draw(
            x,
            y,
            width,
            height,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
    }

    private void drawLabel(int x, int y, int width, int height) {
        if (source() == null) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String label = fr.trimStringToWidth(LABEL, width - 4);
        int textWidth = fr.getStringWidth(label);
        fr.drawStringWithShadow(
            label,
            x + (width - textWidth) / 2,
            y + (height - fr.FONT_HEIGHT) / 2 + BUTTON_TEXT_BASELINE_OFFSET,
            EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
    }

    private @Nullable StationCopyModuleActionModel.Source source() {
        if (editModeController.isActive()) return null;
        if (!(CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility)) return null;
        return StationCopyModuleActionModel.resolve(facility, map.selection());
    }

    private static IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }
}
