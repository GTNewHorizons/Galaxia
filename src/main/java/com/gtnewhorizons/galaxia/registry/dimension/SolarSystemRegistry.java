package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.registry.dimension.asteroidbelts.FrozenBelt;
import com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.registry.dimension.planets.Mars;
import com.gtnewhorizons.galaxia.registry.dimension.planets.Moon;
import com.gtnewhorizons.galaxia.registry.dimension.planets.Panspira;

import cpw.mods.fml.common.FMLLog;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * A registry class for storing all dimensions in the solar system
 */
public final class SolarSystemRegistry {

    private static final List<BasePlanet> BODIES = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<BasePlanet> BY_ID = new Int2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<String, BasePlanet> BY_NAME = new Object2ObjectOpenHashMap<>();
    public static final IntSet GALAXIA_DIMENSIONS = new IntOpenHashSet();

    private static boolean registered = false;

    /**
     * Registers all dimensions. Any newly created planets should be registered here
     */
    public static void registerAll() {
        if (registered) return;
        registered = true;

        registerDimensions(new SpaceStation());
        registerDimensions(new Moon());
        registerDimensions(new Mars());
        registerDimensions(new FrozenBelt());
        registerDimensions(new Panspira());

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
