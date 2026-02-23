package com.gtnewhorizons.galaxia.dimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.dimension.asteroidbelts.FrozenBelt;
import com.gtnewhorizons.galaxia.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.dimension.planets.Hemateria;
import com.gtnewhorizons.galaxia.dimension.planets.Theia;

import cpw.mods.fml.common.FMLLog;

/**
 * A registry class for storing all dimensions in the solar system
 */
public final class SolarSystemRegistry {

    private static final List<BasePlanet> BODIES = new ArrayList<>();
    private static final Map<Integer, BasePlanet> BY_ID = new HashMap<>();
    private static final Map<String, BasePlanet> BY_NAME = new HashMap<>();
    public static final List<Integer> GALAXIA_DIMENSIONS = new ArrayList<>();

    private static boolean registered = false;

    /**
     * Registers all dimensions. Any newly created planets should be registered here
     */
    public static void registerAll() {
        if (registered) return;
        registered = true;

        registerDimensions(new Theia());
        registerDimensions(new Hemateria());
        registerDimensions(new FrozenBelt());

        FMLLog.info("[Galaxia] Registered %d celestial bodies", BODIES.size());
    }

    /**
     * Registers a given dimension
     * 
     * @param planet The planet to register
     */
    private static void registerDimensions(BasePlanet planet) {
        // Gets the basic information required from the planet
        DimensionEnum planetEnum = planet.getPlanetEnum();
        int id = planetEnum.getId();
        String name = planetEnum.getName()
            .toLowerCase();

        // Add to local registry
        BODIES.add(planet);
        BY_ID.put(id, planet);
        BY_NAME.put(name, planet);
        GALAXIA_DIMENSIONS.add(id);

        // Add to game registry - Give warning if trying to override taken ID
        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
            FMLLog.info("[Galaxia] Registered dimension %s (ID %d)", planetEnum.getName(), id);
        } else {
            FMLLog.warning("[Galaxia] Dimension ID %d already taken!", id);
        }
    }

    /**
     * Getter for a specific dimension by ID
     * 
     * @param id The ID of the dimension to get
     * @return The Dimension Definition of the planet or null if not found
     */
    public static DimensionDef getById(int id) {
        BasePlanet planet = BY_ID.get(id);
        return planet != null ? planet.getDef() : null;
    }

    /**
     * Getter for a specific dimension by name
     * 
     * @param name The name of the dimension to get
     * @return The Dimension Definition of the planet or null if not found
     */
    public static DimensionDef getByName(String name) {
        if (name == null) return null;
        BasePlanet planet = BY_NAME.get(name.toLowerCase());
        return planet != null ? planet.getDef() : null;
    }

    /**
     * Gets all planets currently in the registry
     * 
     * @return ArrayList of all bodies in the system
     */
    public static List<BasePlanet> getAllPlanets() {
        return new ArrayList<>(BODIES);
    }
}
