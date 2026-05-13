package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartType;

/**
 * A class defining the limits of module usage per rocket tier
 */
public enum EnumTiers {

    TIER_1(1, ModuleLimit.of(RocketPartType.DECOUPLER, 1), ModuleLimit.of(RocketPartType.FUEL_TANK, 1),
        ModuleLimit.of(RocketPartType.FUNCTIONAL, 1), ModuleLimit.of(RocketPartType.ENGINE, 1),
        ModuleLimit.of(RocketPartType.STRUCTURAL, 0)),

    TIER_2(2, ModuleLimit.of(RocketPartType.DECOUPLER, 1), ModuleLimit.of(RocketPartType.FUEL_TANK, 3),
        ModuleLimit.of(RocketPartType.FUNCTIONAL, 2), ModuleLimit.of(RocketPartType.ENGINE, 3),
        ModuleLimit.of(RocketPartType.STRUCTURAL, 1));

    private final int tier;
    private final Map<RocketPartType, Integer> limits;

    EnumTiers(int tier, ModuleLimit... limits) {
        this.tier = tier;
        this.limits = buildLimits(limits);
    }

    private static Map<RocketPartType, Integer> buildLimits(ModuleLimit... limits) {
        Map<RocketPartType, Integer> map = new HashMap<>();
        for (ModuleLimit limit : limits) {
            map.put(limit.type(), limit.limit());
        }
        return Collections.unmodifiableMap(map);
    }

    public int getLimitFor(RocketPartType category) {
        return limits.getOrDefault(category, 30);
    }

    public boolean isGreaterThanOrEqual(EnumTiers other) {
        return this.tier >= other.tier;
    }

    public int toInt() {
        return tier;
    }
}
