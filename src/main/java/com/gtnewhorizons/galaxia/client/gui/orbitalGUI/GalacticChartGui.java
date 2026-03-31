package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.GalaxiaRegistry;

public class GalacticChartGui {

    private static final int LEFT_PANEL_WIDTH = 216;

    public ModularPanel build(PanelSyncManager syncManager) {
        ModularPanel panel = ModularPanel.defaultPanel("galactic_orbital_map")
            .fullScreenInvisible();

        OrbitalCelestialBody galaxyRoot = GalaxiaRegistry.root();
        int currentDimension = Minecraft.getMinecraft().thePlayer == null ? 0 : Minecraft.getMinecraft().thePlayer.dimension;
        OrbitalCelestialBody currentStar = GalaxiaRegistry.findCurrentStar(currentDimension)
            .orElseGet(() -> GalaxiaRegistry.getPrimaryStar().orElse(galaxyRoot));

        OrbitalMapWidget map = new OrbitalMapWidget(galaxyRoot).withInitialLayer(currentStar);
        CelestialSidebarWidget sidebar = new CelestialSidebarWidget(galaxyRoot, currentStar, map);

        return panel.child(
            (IWidget) sidebar.left(0)
                .top(0)
                .width(LEFT_PANEL_WIDTH)
                .bottom(0))
            .child(
                (IWidget) map.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .right(0)
                    .bottom(0));
    }
}
