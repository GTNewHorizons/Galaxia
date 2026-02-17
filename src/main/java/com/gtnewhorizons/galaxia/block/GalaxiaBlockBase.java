package com.gtnewhorizons.galaxia.block;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.dimension.PlanetEnum;

import cpw.mods.fml.common.registry.GameRegistry;

public class GalaxiaBlockBase {

    private static final Map<PlanetEnum, BlockPlanetGalaxia> planetBlocks = new HashMap<>();

    static void reg(PlanetEnum planet, BlockVariant... variants) {
        if (variants.length == 0) {
            throw new IllegalArgumentException("Invalid variant count for " + planet.getName());
        }
        BlockPlanetGalaxia block = new BlockPlanetGalaxia(planet.getName(), variants);
        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());
        planetBlocks.put(planet, block);
    }

    public static Block get(PlanetEnum planet) {
        return planetBlocks.get(planet);
    }

    public static Block get(PlanetEnum planet, String variant) {
        BlockPlanetGalaxia block = planetBlocks.get(planet);
        if (block == null) {
            throw new IllegalArgumentException("Planet not registered: " + planet);
        }
        boolean found = false;
        for (int i = 0; i < block.getVariantCount(); i++) {
            if (block.getVariantSuffix(i)
                .equalsIgnoreCase(variant)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Variant '" + variant + "' not found for planet " + planet.getName());
        }
        return block;
    }
}
