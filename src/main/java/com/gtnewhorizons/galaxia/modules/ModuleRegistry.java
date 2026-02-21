package com.gtnewhorizons.galaxia.modules;

import java.util.HashMap;
import java.util.Map;

public class ModuleRegistry {

    private static final Map<String, ModuleType> REGISTRY = new HashMap<>();

    public static void register(ModuleType type) {
        REGISTRY.put(type.getId(), type);
    }

    public static ModuleType get(String id) {
        return REGISTRY.get(id);
    }

    public static void init() {
        for (ModuleTypes module : ModuleTypes.values()) {
            register(module.data);
        }
    }
}
