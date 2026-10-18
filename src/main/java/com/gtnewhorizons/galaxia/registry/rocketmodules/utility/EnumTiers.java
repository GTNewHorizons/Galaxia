package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Kind;

public enum EnumTiers {

    TIER_1(1, Map.of(Kind.DECOUPLER, 1, Kind.FUEL_TANK, 1, Kind.CARGO, 1, Kind.ENGINE, 1, Kind.STRUCTURAL, 0)),
    TIER_2(2, Map.of(Kind.DECOUPLER, 1, Kind.FUEL_TANK, 3, Kind.CARGO, 2, Kind.ENGINE, 3, Kind.STRUCTURAL, 1));

    private final int tier;
    private final Map<Kind, Integer> limits;

    EnumTiers(int tier, Map<Kind, Integer> limits) {
        this.tier = tier;
        this.limits = Map.copyOf(limits);
    }

    public int getLimitFor(RocketPartDef part) {
        return limits.getOrDefault(part.kind(), 30);
    }

    public boolean isGreaterThanOrEqual(EnumTiers other) {
        return tier >= other.tier;
    }
}
