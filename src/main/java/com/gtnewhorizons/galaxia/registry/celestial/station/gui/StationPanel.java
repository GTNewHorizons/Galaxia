package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.gtnewhorizons.galaxia.client.EnumColors;

public class StationPanel extends ModularPanel {

    public StationPanel(@NotNull String name) {
        super(name);

        background(
            new Rectangle().color(EnumColors.STATION_PANEL_BG.getColor()),
            new Rectangle().hollow(2)
                .color(EnumColors.AIRLOCK_PANEL_BORDER.getColor()));

    }

    public static StationPanel defaultPanel(@NotNull String name) {
        return defaultPanel(name, 176, 166);
    }

    public static StationPanel defaultPanel(@NotNull String name, int width, int height) {
        return (StationPanel) new StationPanel(name).size(width, height);
    }
}
