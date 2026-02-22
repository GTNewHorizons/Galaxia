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

public final class SolarSystemRegistry {

    private static final List<BasePlanet> BODIES = new ArrayList<>();
    private static final Map<Integer, BasePlanet> BY_ID = new HashMap<>();
    private static final Map<String, BasePlanet> BY_NAME = new HashMap<>();
    public static final List<Integer> GALAXIA_DIMENSIONS = new ArrayList<>();

    private static boolean registered = false;

    public static void registerAll() {
        if (registered) return;
        registered = true;

        registerDimensions(new Theia());
        registerDimensions(new Hemateria());
        registerDimensions(new FrozenBelt());

        FMLLog.info("[Galaxia] Registered %d celestial bodies", BODIES.size());
    }

    private static void registerDimensions(BasePlanet planet) {
        DimensionEnum planetEnum = planet.getPlanetEnum();
        int id = planetEnum.getId();
        String name = planetEnum.getName()
            .toLowerCase();

        BODIES.add(planet);
        BY_ID.put(id, planet);
        BY_NAME.put(name, planet);
        GALAXIA_DIMENSIONS.add(id);

        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
            FMLLog.info("[Galaxia] Registered dimension %s (ID %d)", planetEnum.getName(), id);
        } else {
            FMLLog.warning("[Galaxia] Dimension ID %d already taken!", id);
        }
    }

    public static DimensionDef getById(int id) {
        BasePlanet planet = BY_ID.get(id);
        return planet != null ? planet.getDef() : null;
    }

    public static DimensionDef getByName(String name) {
        if (name == null) return null;
        BasePlanet planet = BY_NAME.get(name.toLowerCase());
        return planet != null ? planet.getDef() : null;
    }

    public static List<BasePlanet> getAllPlanets() {
        return new ArrayList<>(BODIES);
    }
}
