package com.gtnewhorizons.galaxia.rocketmodules.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.CapsuleModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.EngineModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.FuelTankModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.LanderModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.RocketCoreModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.StorageModule;

public final class ModuleRegistry {

    private static final Map<Integer, RocketModule> MODULES = new HashMap<>();

    public static void register(RocketModule module) {
        MODULES.put(module.getId(), module);
    }

    public static RocketModule fromId(int id) {
        return MODULES.get(id);
    }

    public static List<RocketModule> getAll() {
        return new ArrayList<>(MODULES.values());
    }

    public static void registerAllModules() {
        // Basic Tier Modules
        // TODO: Replace model name for Lander and localize nicely (They will be
        // watching)
        // TODO: Replace model name for Core Module and localize nicely (I'll be
        // watching)
        new RocketCoreModule(0, "Basic Rocket Core", 5.0, 3.0, 500.0, "fuel_tank_3x5x3", EnumTiers.TIER_1);
        new FuelTankModule(1, "Basic Fuel Tank", 5.0, 3.0, 1200.0, "fuel_tank_3x5x3", 8000.0);
        new CapsuleModule(2, "Basic Capsule Module", 2.5, 3.0, 450.0, "capsule_3x2.5x3", -1.75, 1);
        new StorageModule(3, "Basic Storage Module", 4.0, 3.0, 900.0, "storage_unit_3x4x3", 100);
        new EngineModule(4, "Basic Fuel Engine", 0.5, 3.0, 250.0, "engine_3x1x3", 6000.0);
        new LanderModule(5, "Basic Lander", 2.5, 3.0, 250.0, "capsule_3x2.5x3", -1.75, 1);
        // Add new tiers in same format below:
    }
}
