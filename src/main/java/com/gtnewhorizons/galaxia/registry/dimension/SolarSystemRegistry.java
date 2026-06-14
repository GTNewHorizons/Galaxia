package com.gtnewhorizons.galaxia.registry.dimension;

import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

import cpw.mods.fml.common.FMLLog;

/**
 * A registry class for storing all dimensions in the solar system
 */
public final class SolarSystemRegistry {

    private static boolean registered = false;

    public static void registerAll() {
        if (registered) return;
        registered = true;

        int count = 0;
        for (DimensionDef def : CelestialDimensionMaterializer.materializePlayableDefinitions()) {
            registerDimension(def);
            count++;
        }

        FMLLog.info("[Galaxia] Registered %d celestial dimensions", count);
    }

    private static void registerDimension(DimensionDef def) {
        int id = def.id();

        DimensionManager.registerProviderType(id, def.provider(), def.keepLoaded());

        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
            FMLLog.info("[Galaxia] Registered dimension %s (ID %d)", def.name(), id);
        } else {
            FMLLog.warning("[Galaxia] Dimension ID %d already taken!", id);
        }
    }

    public static DimensionDef getById(int id) {
        DimensionEnum dimension = DimensionEnum.fromId(id);
        if (dimension == null) return null;
        return CelestialRegistry.findByDimension(dimension)
            .map(CelestialDimensionMaterializer::materializeDefinition)
            .orElse(null);
    }
}
