package com.gtnewhorizons.galaxia.modules;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry class for Rocket modules
 */
public class ModuleRegistry {

    private static final Map<String, ModuleType> REGISTRY = new HashMap<>();

    /**
     * Registers a new module type to the local registry
     * 
     * @param type The module type to register
     */
    public static void register(ModuleType type) {
        REGISTRY.put(type.getId(), type);
    }

    /**
     * Gets the module from the registry with given ID
     * 
     * @param id The ID to get
     * @return The Module Type with that ID
     */
    public static ModuleType get(String id) {
        return REGISTRY.get(id);
    }

    /**
     * Initializes the registry
     */
    public static void init() {
        for (ModuleTypes module : ModuleTypes.values()) {
            register(module.data);
        }
    }
}
