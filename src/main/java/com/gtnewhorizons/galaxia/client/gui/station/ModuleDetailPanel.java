package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleDetailPanel extends ParentWidget<ModuleDetailPanel> {

    private static final int CONTENT_PADDING = 10;

    private final StationMapWidget map;
    private final @Nullable StationTilePickerController tilePickerController;

    public ModuleDetailPanel(StationMapWidget map) {
        this(map, null);
    }

    public ModuleDetailPanel(StationMapWidget map, @Nullable StationTilePickerController tilePickerController) {
        this.map = map;
        this.tilePickerController = tilePickerController;
    }

    @Override
    public boolean canHoverThrough() {
        return isPickerActive();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (isPickerActive()) return;
        StationTileCoord selected = map.selection();
        if (selected == null) return;

        AutomatedFacility facility = resolveFacility();
        if (facility == null) return;

        StationLayout layout = facility.stationLayout();
        if (layout == null) return;

        PlacedTile tile = layout.get(selected);
        if (tile == null || tile.isCore()) return;

        ModuleInstance module = tile.module();
        if (module == null) return;

        CelestialAsset.ID facilityId = map.assetId();

        int x = 0;
        int y = 0;
        int width = getArea().width;
        int height = getArea().height;

        BorderedRect.draw(
            x,
            y,
            width,
            height,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());

        ModuleDetailTextRegistry.collect(new ModuleDetailTextRegistry.Context(facility, layout, module, facilityId))
            .draw(x + CONTENT_PADDING, y + CONTENT_PADDING);
    }

    private @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID id = map.assetId();
        return id != null && CelestialClient.getByAssetId(id) instanceof AutomatedFacility f ? f : null;
    }

    private boolean isPickerActive() {
        return tilePickerController != null && tilePickerController.isActive();
    }

}
