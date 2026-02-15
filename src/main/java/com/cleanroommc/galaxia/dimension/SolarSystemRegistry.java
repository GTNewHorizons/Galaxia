package com.cleanroommc.galaxia.dimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.DimensionManager;

import com.cleanroommc.galaxia.dimension.planets.BasePlanet;
import com.cleanroommc.galaxia.dimension.planets.Calx;
import com.cleanroommc.galaxia.dimension.planets.Dunia;

import cpw.mods.fml.common.FMLLog;

public final class SolarSystemRegistry {

    private static final List<DimensionDef> DIMENSIONS = new ArrayList<>();
    private static final Map<String, DimensionDef> BY_NAME = new HashMap<>();
    private static final Map<Integer, DimensionDef> BY_ID = new HashMap<>();

    private static boolean registered = false;

    public static void registerAll() {
        if (registered) return;
        registered = true;

        registerPlanet(new Calx());
        registerPlanet(new Dunia());

        FMLLog.info("[Galaxia] registered %d celestial bodies", DIMENSIONS.size());
    }

    private static void registerPlanet(BasePlanet planet) {
        DimensionDef def = planet.buildDimension();

        int dimId = def.id;

        DimensionManager.registerProviderType(dimId, def.provider, true);
        if (!DimensionManager.isDimensionRegistered(dimId)) {
            DimensionManager.registerDimension(dimId, dimId);
            FMLLog.info("[Galaxia] Registered dim %s (ID %d)", def.name, dimId);
        } else {
            FMLLog.warning("[Galaxia] Dim ID %d already taken!", dimId);
        }

        BY_ID.put(dimId, def);
        BY_NAME.put(def.name.toLowerCase(), def);

        DIMENSIONS.add(def);
    }

    public static DimensionDef getByName(String name) {
        return BY_NAME.get(name.toLowerCase());
    }

    public static DimensionDef getById(int id) {
        return BY_ID.get(id);
    }

    public static List<DimensionDef> getAll() {
        return new ArrayList<>(DIMENSIONS);
    }
}
