package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

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

    public ModuleDetailPanel(StationMapWidget map) {
        this.map = map;
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
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

        String text = "Module: " + module.kind()
            .name();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(
            text,
            x + CONTENT_PADDING,
            y + CONTENT_PADDING,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID assetId = map.assetId();
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
    }
}
