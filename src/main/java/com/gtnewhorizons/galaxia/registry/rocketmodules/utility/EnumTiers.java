package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FunctionalPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StructuralPartDef;

public enum EnumTiers {

    TIER_1(1, ModuleLimit.of(DecouplerPartDef.class, 1), ModuleLimit.of(FuelTankPartDef.class, 1),
        ModuleLimit.of(FunctionalPartDef.class, 1), ModuleLimit.of(EnginePartDef.class, 1),
        ModuleLimit.of(StructuralPartDef.class, 0)),

    TIER_2(2, ModuleLimit.of(DecouplerPartDef.class, 1), ModuleLimit.of(FuelTankPartDef.class, 3),
        ModuleLimit.of(FunctionalPartDef.class, 2), ModuleLimit.of(EnginePartDef.class, 3),
        ModuleLimit.of(StructuralPartDef.class, 1));

    private final int tier;

    private final Map<Class<? extends IRocketPartDef>, Integer> limits;

    EnumTiers(int tier, ModuleLimit... limits) {
        this.tier = tier;
        this.limits = buildLimits(limits);
    }

    private static Map<Class<? extends IRocketPartDef>, Integer> buildLimits(ModuleLimit... limits) {

        Map<Class<? extends IRocketPartDef>, Integer> map = new HashMap<>();

        for (ModuleLimit limit : limits) {
            map.put(limit.type(), limit.limit());
        }

        return Collections.unmodifiableMap(map);
    }

    public int getLimitFor(IRocketPartDef part) {
        return getLimitFor(part.getClass());
    }

    public int getLimitFor(Class<? extends IRocketPartDef> type) {
        return limits.getOrDefault(type, 30);
    }

    public boolean isGreaterThanOrEqual(EnumTiers other) {
        return this.tier >= other.tier;
    }

    public int toInt() {
        return tier;
    }
}
