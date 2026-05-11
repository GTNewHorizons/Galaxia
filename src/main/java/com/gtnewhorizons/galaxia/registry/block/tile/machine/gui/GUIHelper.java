package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.gtnewhorizons.galaxia.client.EnumTextures;

public class GUIHelper {

    public static ProgressWidget createEnergyBar(IntSyncValue current, IntSyncValue max, int x, int y) {

        return new ProgressWidget().value(new DoubleValue.Dynamic(() -> {
            int maxValue = max.getIntValue();
            if (maxValue <= 0) return 0;

            return (double) current.getIntValue() / maxValue;
        }, null))
            .texture(EnumTextures.TEMP_BG.getImage(), EnumTextures.TEMP_FILL_HOT.getImage(), 160)
            .direction(ProgressWidget.Direction.RIGHT)
            .pos(x, y)
            .size(160, 12);
    }

    public static ProgressWidget createOxygenBar(IntSyncValue current, IntSyncValue max, int x, int y) {

        return new ProgressWidget().value(new DoubleValue.Dynamic(() -> {
            int maxValue = max.getIntValue();
            if (maxValue <= 0) return 0;

            return (double) current.getIntValue() / maxValue;
        }, null))
            .texture(EnumTextures.OXYGEN_BG.getImage(), EnumTextures.OXYGEN_FILL.getImage(), 160)
            .direction(ProgressWidget.Direction.RIGHT)
            .pos(x, y)
            .size(160, 12);
    }
}
