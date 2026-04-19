package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.util.StatCollector;

public enum StationVisionLayer {

    BASE,
    ENERGY;

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.station.vision_layer." + this.name()
                .toLowerCase());
    }
}
