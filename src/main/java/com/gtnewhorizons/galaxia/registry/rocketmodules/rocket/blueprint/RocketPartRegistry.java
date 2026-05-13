package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RocketPartRegistry {

    private static final RocketPartRegistry INSTANCE = new RocketPartRegistry();
    private final Map<Integer, RocketPartDef> parts = new HashMap<>();

    public static RocketPartRegistry instance() {
        return INSTANCE;
    }

    public void register(RocketPartDef def) {
        parts.put(def.id(), def);
    }

    public RocketPartDef get(int id) {
        return parts.get(id);
    }

    public List<RocketPartDef> getAll() {
        return new ArrayList<>(parts.values());
    }

    public void registerAll() {
        register(
            new RocketPartDef(0, "Basic Capsule", RocketPartType.CAPSULE, 3.0, 2.5, 450.0, 0, 0, -1, 1, null, null));
        register(
            new RocketPartDef(
                1,
                "Basic Fuel Tank",
                RocketPartType.FUEL_TANK,
                3.0,
                5.0,
                1200.0,
                8000.0,
                0,
                -1,
                0,
                null,
                null));
        register(
            new RocketPartDef(
                2,
                "Basic Engine",
                RocketPartType.ENGINE,
                3.0,
                3.46,
                250.0,
                0,
                6000.0,
                -1,
                0,
                null,
                null));
        register(
            new RocketPartDef(3, "Basic Decoupler", RocketPartType.DECOUPLER, 3.0, 1.0, 100.0, 0, 0, 1, 0, null, null));
        register(new RocketPartDef(4, "Basic Lander", RocketPartType.LANDER, 3.0, 2.5, 250.0, 0, 0, -1, 1, null, null));
        register(new RocketPartDef(5, "Basic Rider", RocketPartType.RIDER, 3.0, 5.0, 250.0, 0, 0, -1, 6, null, null));
        register(
            new RocketPartDef(6, "Basic Storage", RocketPartType.FUNCTIONAL, 3.0, 4.0, 900.0, 0, 0, -1, 0, null, null));
    }
}
