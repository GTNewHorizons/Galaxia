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

    public static RocketPartRegistry instance() {
        return INSTANCE;
    }

    public void register(IRocketPartDef def) {
        parts.put(def.id(), def);
    }

    public IRocketPartDef get(int id) {
        return parts.get(id);
    }

    public List<IRocketPartDef> getAll() {
        return new ArrayList<>(parts.values());
    }

    // spotless:off
    public void registerAll() {
        int id = 0;

        register(
            new CapsulePartDef(
                id++,
                "Basic Capsule",
                3,
                3,
                450,
                "capsule_1"));

        register(
            new FuelTankPartDef(
                id++,
                "Basic Fuel Tank",
                3,
                5,
                1200,
                8000.0,
                "fuel_tank_1"));

        register(
            new EnginePartDef(
                id++,
                "Basic Engine",
                3,
                4,
                250,
                6000.0,
                "engine_1"));

        register(
            new DecouplerPartDef(
                id++,
                "Basic Decoupler",
                3,
                1,
                100,
                1,
                "decoupler_1"));

        register(
            new LanderPartDef(
                id++,
                "Basic Lander",
                3,
                3,
                250,
                null));

        register(
            new RiderPartDef(
                id++,
                "Basic Rider",
                3,
                5,
                250,
                6,
                null));

        register(
            new FunctionalPartDef(
                id++,
                "Basic Storage",
                3,
                4,
                900,
                "storage_unit_1"));
    }
    // spotless:on
}
