package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;

public record ModuleLimit(Class<? extends IRocketPartDef> type, int limit) {

    public static ModuleLimit of(Class<? extends IRocketPartDef> type, int limit) {
        return new ModuleLimit(type, limit);
    }
}
