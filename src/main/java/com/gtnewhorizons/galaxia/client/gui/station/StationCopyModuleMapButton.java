package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

final class StationCopyModuleMapButton extends ButtonWidget<StationCopyModuleMapButton> {

    private static final String LABEL = "Copy Module";
    private static final int BUTTON_TEXT_BASELINE_OFFSET = 1;

    private final @Nullable CelestialAsset.ID assetId;
    private final StationMapWidget map;
    private final StationTilePickerController tilePickerController;

    StationCopyModuleMapButton(@Nullable CelestialAsset.ID assetId, StationMapWidget map,
        StationTilePickerController tilePickerController, boolean creativeBuildMode) {
        this.assetId = assetId;
        this.map = map;
        this.tilePickerController = tilePickerController;
        background(DrawableCommand.asDrawable((ctx, x, y, w, h) -> drawButton(x, y, w, h, false)));
        hoverBackground(DrawableCommand.asDrawable((ctx, x, y, w, h) -> drawButton(x, y, w, h, true)));
        overlay(DrawableCommand.asDrawable((ctx, x, y, w, h) -> drawLabel(x, y, w, h)));
        onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            ModuleInstance source = source();
            if (source == null) return false;
            StationManagementScreen.openCopyBuildPicker(assetId, source.id, creativeBuildMode);
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

    private @Nullable ModuleInstance source() {
        if (tilePickerController.isActive()) return null;
        if (!(CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility)) return null;
        if (facility.stationLayout() == null || map.selection() == null) return null;
        ModuleInstance module = facility.stationLayout()
            .moduleAt(map.selection());
        return module == null ? null : facility.moduleById(module.id);
    }

}
