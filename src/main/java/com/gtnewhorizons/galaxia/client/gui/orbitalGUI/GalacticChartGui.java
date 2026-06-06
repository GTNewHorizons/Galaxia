package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public class GalacticChartGui {

    private static final int LEFT_PANEL_WIDTH = 216;

    public ModularPanel build(PanelSyncManager syncManager, EntityPlayer player) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        ModularPanel panel = ModularPanel.defaultPanel("galactic_orbital_map")
            .fullScreenInvisible();
        CelestialObject galaxyRoot = GalaxiaCelestialAPI.root();
        int currentDimension = player.dimension;
        CelestialObject currentStar = GalaxiaCelestialAPI.findCurrentStar(currentDimension)
            .orElseGet(
                () -> GalaxiaCelestialAPI.getPrimaryStar()
                    .orElse(galaxyRoot));
        TextFieldWidget renameField = new TextFieldWidget().left(LEFT_PANEL_WIDTH)
            .top(-1000)
            .width(180)
            .height(22)
            .setMaxLength(48)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText("Asset name")
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        GalacticMapWidget galacticMap = new GalacticMapWidget(galaxyRoot, currentStar, renameField);
        CelestialSidebarWidget sidebar = new CelestialSidebarWidget(galaxyRoot, currentStar, galacticMap.mapWidget());
        return panel.child(
            (IWidget) sidebar.left(0)
                .top(0)
                .width(LEFT_PANEL_WIDTH)
                .heightRel(1f))
            .child(
                (IWidget) galacticMap.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .widthRelOffset(1f, -LEFT_PANEL_WIDTH)
                    .heightRel(1f))
            .child((IWidget) renameField);
    }
}
