package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FunctionalPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RiderPartDef;

/**
 * Registers rocket modules and their properties from {@link IRocketPartDef} implementations.
 */
public class RocketPartRegistry {

    private static final RocketPartRegistry INSTANCE = new RocketPartRegistry();
    private final Map<Integer, IRocketPartDef> parts = new HashMap<>();
    private final Map<RocketPartCategory, List<IRocketPartDef>> categoryMap = new HashMap<>();

    public static CapsulePartDef CAPSULE_T1;
    public static FuelTankPartDef FUEL_TANK_T1;
    public static EnginePartDef ENGINE_T1;
    public static DecouplerPartDef DECOUPLER_T1;
    public static LanderPartDef LANDER_T1;
    public static RiderPartDef RIDER_T1;
    public static FunctionalPartDef STORAGE_T1;

    public static RocketPartRegistry instance() {
        return INSTANCE;
    }

    public void register(IRocketPartDef def, RocketPartCategory cat) {
        parts.put(def.id(), def);
        categoryMap.computeIfAbsent(cat, k -> new ArrayList<>())
            .add(def);
    }

    public IRocketPartDef get(int id) {
        return parts.get(id);
    }

    public IRocketPartDef getByName(String name) {
        for (IRocketPartDef def : parts.values()) {
            if (def.name()
                .equals(name)) return def;
        }
        return null;
    }

    public <T extends IRocketPartDef> List<T> getByClass(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (IRocketPartDef def : parts.values()) {
            if (clazz.isInstance(def)) {
                result.add(clazz.cast(def));
            }
        }
        return result;
    }

    public List<IRocketPartDef> getByCategory(RocketPartCategory cat) {
        return categoryMap.getOrDefault(cat, new ArrayList<>());
    }

    public List<IRocketPartDef> getAll() {
        return new ArrayList<>(parts.values());
    }

    public void registerAll() {
        int id = 0;

        CAPSULE_T1 = new CapsulePartDef(id++, "Basic Capsule", 3, 3, 450, "capsule_1");
        register(CAPSULE_T1, RocketPartCategory.CAPSULES);

        FUEL_TANK_T1 = new FuelTankPartDef(id++, "Basic Fuel Tank", 3, 5, 1200, 8000.0, "fuel_tank_1");
        register(FUEL_TANK_T1, RocketPartCategory.FUEL_TANKS);

        ENGINE_T1 = new EnginePartDef(id++, "Basic Engine", 3, 4, 250, 6000.0, "engine_1");
        register(ENGINE_T1, RocketPartCategory.LIQUID_ENGINES);

        DECOUPLER_T1 = new DecouplerPartDef(id++, "Basic Decoupler", 3, 1, 100, 1, "decoupler_1");
        register(DECOUPLER_T1, RocketPartCategory.DECOUPLERS);

        LANDER_T1 = new LanderPartDef(id++, "Basic Lander", 3, 3, 250, null);
        register(LANDER_T1, RocketPartCategory.CABINS);

        RIDER_T1 = new RiderPartDef(id++, "Basic Rider", 3, 5, 250, 6, null);
        register(RIDER_T1, RocketPartCategory.CABINS);

        STORAGE_T1 = new FunctionalPartDef(id++, "Basic Storage", 3, 4, 900, "storage_unit_1");
        register(STORAGE_T1, RocketPartCategory.STRUCTURAL);
    }
}
