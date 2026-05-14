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
                3.0,
                2.5,
                450.0,
                "capsule_1"));

        register(
            new FuelTankPartDef(
                id++,
                "Basic Fuel Tank",
                3.0,
                5.0,
                1200.0,
                8000.0,
                null));

        register(
            new EnginePartDef(
                id++,
                "Basic Engine",
                3.0,
                3.5,
                250.0,
                6000.0,
                null));

        register(
            new DecouplerPartDef(
                id++,
                "Basic Decoupler",
                3.0,
                1.0,
                100.0,
                1,
                null));

        register(
            new LanderPartDef(
                id++,
                "Basic Lander",
                3.0,
                2.5,
                250.0,
                null));

        register(
            new RiderPartDef(
                id++,
                "Basic Rider",
                3.0,
                5.0,
                250.0,
                6,
                null));

        register(
            new FunctionalPartDef(
                id++,
                "Basic Storage",
                3.0,
                4.0,
                900.0,
                null));
    }
    // spotless:on
}
