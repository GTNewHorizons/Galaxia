package com.gtnewhorizons.galaxia.rocketmodules.rocket;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// TODO: Add more tier caps
public enum EnumTiers {

    TIER_1(1,
        limitsMap(
            EnumModuleCategory.PAYLOAD,
            1,
            EnumModuleCategory.FUEL_TANK,
            1,
            EnumModuleCategory.FUNCTIONAL,
            1,
            EnumModuleCategory.ENGINE,
            1,
            EnumModuleCategory.STRUCTURAL,
            0)),
    TIER_2(2,
        limitsMap(
            EnumModuleCategory.PAYLOAD,
            1,
            EnumModuleCategory.FUEL_TANK,
            3,
            EnumModuleCategory.FUNCTIONAL,
            2,
            EnumModuleCategory.ENGINE,
            3,
            EnumModuleCategory.STRUCTURAL,
            1));

    private int tier;
    private final Map<EnumModuleCategory, Integer> limits;

    EnumTiers(int tier, Map<EnumModuleCategory, Integer> limits) {
        this.tier = tier;
        this.limits = limits;
    }

    public int getLimitFor(EnumModuleCategory cat) {
        return limits.getOrDefault(cat, 30);
    }

    public boolean isGreaterThanOrEqual(EnumTiers other) {
        return this.tier >= other.tier;
    }

    private static Map<EnumModuleCategory, Integer> limitsMap(Object... pairs) {
        Map<EnumModuleCategory, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((EnumModuleCategory) pairs[i], (Integer) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    public int toInt() {
        return this.tier;
    }
}
