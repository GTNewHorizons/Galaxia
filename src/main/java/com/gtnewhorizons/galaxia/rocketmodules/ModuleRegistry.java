package com.gtnewhorizons.galaxia.rocketmodules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.rocketmodules.modules.CapsuleModule;
import com.gtnewhorizons.galaxia.rocketmodules.modules.EngineModule;
import com.gtnewhorizons.galaxia.rocketmodules.modules.FuelTankModule;
import com.gtnewhorizons.galaxia.rocketmodules.modules.StorageModule;

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
        new FuelTankModule();
        new CapsuleModule();
        new StorageModule();
        new EngineModule();
    }
}
