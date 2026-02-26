package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.orbitalGUI.GalaxiaRegistry;
import com.gtnewhorizons.galaxia.orbitalGUI.flightplan.Spacecraft;

public class GalacticChartGui {

    public ModularPanel build(PanelSyncManager syncManager) {
        ModularPanel panel = ModularPanel.defaultPanel("galactic_orbital_map")
            .fullScreenInvisible();

        OrbitalMapWidget map = new OrbitalMapWidget(GalaxiaRegistry.ROOT);

        Spacecraft testRocket = new Spacecraft(GalaxiaRegistry.ROOT.children().get(0), 0.0, 0.0, 0.0, 0.0, 100000, 300);
        map.setSpacecraft(testRocket);

        CelestialSidebarWidget sidebar = new CelestialSidebarWidget(GalaxiaRegistry.ROOT, map);

        return panel.child(
            (IWidget) sidebar.left(0)
                .top(0)
                .width(280)
                .bottom(0))
            .child(
                (IWidget) map.left(280)
                    .top(0)
                    .right(0)
                    .bottom(0));
    }
}
