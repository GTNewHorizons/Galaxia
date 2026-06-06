package com.gtnewhorizons.galaxia.registry.hazards;

import net.minecraft.util.StatCollector;

public enum HazardWarnings {

    FINE(StatCollector.translateToLocal("galaxia.hazard.fine")),
    FREEZING(StatCollector.translateToLocal("galaxia.hazard.freezing")),
    BURNING(StatCollector.translateToLocal("galaxia.hazard.burning")),
    LOW_PRESSURE(StatCollector.translateToLocal("galaxia.hazard.low_pressure")),
    HIGH_PRESSURE(StatCollector.translateToLocal("galaxia.hazard.high_pressure")),
    HIGH_RADIATION(StatCollector.translateToLocal("galaxia.hazard.high_radiation")),
    SPORES(StatCollector.translateToLocal("galaxia.hazard.spores")),
    WITHER(StatCollector.translateToLocal("galaxia.hazard.wither")),
    LOW_OXYGEN(StatCollector.translateToLocal("galaxia.hazard.low_oxygen")),
    NO_OXYGEN(StatCollector.translateToLocal("galaxia.hazard.no_oxygen")),
    NO_ZEROG_MOVEMENT(StatCollector.translateToLocal("galaxia.hazard.no_zerog_movement")),

    ;

    public final String message;

    HazardWarnings(String message) {
        this.message = message;
    }
}
