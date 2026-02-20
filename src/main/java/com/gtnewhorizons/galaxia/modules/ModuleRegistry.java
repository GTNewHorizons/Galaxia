package com.gtnewhorizons.galaxia.modules;

public class ModuleRegistry {

    private static final java.util.Map<String, ModuleType> REGISTRY = new java.util.HashMap<>();

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
