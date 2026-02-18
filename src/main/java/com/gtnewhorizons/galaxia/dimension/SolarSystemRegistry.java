package com.gtnewhorizons.galaxia.dimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.dimension.asteroidbelts.FrozenBelt;
import com.gtnewhorizons.galaxia.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.dimension.planets.Calx;
import com.gtnewhorizons.galaxia.dimension.planets.Dunia;

import cpw.mods.fml.common.FMLLog;

public final class SolarSystemRegistry {

    private static final List<BasePlanet> BODIES = new ArrayList<>();
    private static final Map<Integer, BasePlanet> BY_ID = new HashMap<>();
    private static final Map<String, BasePlanet> BY_NAME = new HashMap<>();

    private static boolean registered = false;

    public static void registerAll() {
        if (registered) return;
        registered = true;

        registerDimensions(new Calx());
        registerDimensions(new Dunia());
        registerDimensions(new FrozenBelt());

        FMLLog.info("[Galaxia] Registered %d celestial bodies", BODIES.size());
    }

    private static void registerDimensions(BasePlanet planet) {
        DimensionEnum e = planet.getPlanetEnum();
        int id = e.getId();
        String name = e.getName()
            .toLowerCase();

        BODIES.add(planet);
        BY_ID.put(id, planet);
        BY_NAME.put(name, planet);

        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
            FMLLog.info("[Galaxia] Registered dimension %s (ID %d)", e.getName(), id);
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
