package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartType;

public record ModuleLimit(RocketPartType type, int limit) {

    public static ModuleLimit of(RocketPartType type, int limit) {
        return new ModuleLimit(type, limit);
    }
}
