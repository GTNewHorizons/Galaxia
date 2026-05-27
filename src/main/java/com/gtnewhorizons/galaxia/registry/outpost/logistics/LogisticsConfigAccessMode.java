package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

public enum LogisticsConfigAccessMode {

    FULL,
    IMPORT_ONLY;

    public LogisticsResourceConfig sanitize(LogisticsResourceConfig config) {
        if (config == null) return LogisticsResourceConfig.DEFAULT;
        return this == IMPORT_ONLY ? config.withSupplyEnabled(false) : config;
    }

    public boolean canEditSupply() {
        return this == FULL;
    }
}
