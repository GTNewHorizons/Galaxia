package com.gtnewhorizons.galaxia.registry.outpost.station;

import net.minecraft.util.StatCollector;

public enum StationModuleCategory {

    COMMAND,
    MINING_SUPPORT,
    LOGISTICS,
    STORAGE,
    POWER,
    PROCESSING,
    HABITATION;

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.station.module.category." + this.name()
                .toLowerCase());
    }
}
