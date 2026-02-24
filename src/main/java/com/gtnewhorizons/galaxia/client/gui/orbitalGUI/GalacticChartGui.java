package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.orbitalGUI.GalaxiaRegistry;

public class GalacticChartGui {

    public ModularPanel build(PanelSyncManager syncManager) {
        ModularPanel panel = ModularPanel.defaultPanel("galactic_orbital_map", 520, 420);

        OrbitalMapWidget map = new OrbitalMapWidget(GalaxiaRegistry.ROOT);

        return panel.child(map)
            .size(498, 382)
            .align(Alignment.CENTER);
    }
}
