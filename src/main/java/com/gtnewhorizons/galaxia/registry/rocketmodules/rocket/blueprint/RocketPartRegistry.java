package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;

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
            new RocketPartDef.CapsulePartDef(
                id++,
                "Basic Capsule",
                3.0,
                2.5,
                450.0,
                null,
                null));

        register(
            new RocketPartDef.FuelTankPartDef(
                id++,
                "Basic Fuel Tank",
                3.0,
                5.0,
                1200.0,
                8000.0,
                null,
                null));

        register(
            new RocketPartDef.EnginePartDef(
                id++,
                "Basic Engine",
                3.0,
                3.5,
                250.0,
                6000.0,
                null,
                null));

        register(
            new RocketPartDef.DecouplerPartDef(
                id++,
                "Basic Decoupler",
                3.0,
                1.0,
                100.0,
                1,
                null,
                null));

        register(
            new RocketPartDef.LanderPartDef(
                id++,
                "Basic Lander",
                3.0,
                2.5,
                250.0,
                null,
                null));

        register(
            new RocketPartDef.RiderPartDef(
                id++,
                "Basic Rider",
                3.0,
                5.0,
                250.0,
                6,
                null,
                null));

        register(
            new RocketPartDef.FunctionalPartDef(
                id++,
                "Basic Storage",
                3.0,
                4.0,
                900.0,
                null,
                null));
    }
    // spotless:on
}
