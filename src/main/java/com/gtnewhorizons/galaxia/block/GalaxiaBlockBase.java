package com.gtnewhorizons.galaxia.block;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

import cpw.mods.fml.common.registry.GameRegistry;

public class GalaxiaBlockBase {

    private static final Map<DimensionEnum, BlockPlanetGalaxia> planetBlocks = new HashMap<>();
    private static final Map<DimensionEnum, Item> planetDustMap = new HashMap<>();

    static void reg(DimensionEnum planet, BlockVariant... variants) {
        if (variants.length == 0) {
            throw new IllegalArgumentException("Invalid variant count for " + planet.getName());
        }
        BlockPlanetGalaxia block = new BlockPlanetGalaxia(planet.getName(), variants);
        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());
        planetBlocks.put(planet, block);
    }

    static void reg(DimensionEnum planet, GalaxiaItemList dustEnum, BlockVariant... variants) {
        if (variants.length == 0) {
            throw new IllegalArgumentException("Invalid variant count for " + planet.getName());
        }
        Item dustItem = dustEnum.getItem();
        BlockPlanetGalaxia block = new BlockPlanetGalaxia(planet.getName(), dustItem, variants);
        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());

        planetBlocks.put(planet, block);
        planetDustMap.put(planet, dustItem);
    }

    public static Block get(DimensionEnum planet) {
        return planetBlocks.get(planet);
    }

    public static BlockMeta get(DimensionEnum planet, String variant) {
        BlockPlanetGalaxia block = planetBlocks.get(planet);
        if (block == null) {
            throw new IllegalArgumentException("Planet not registered: " + planet);
        }
        boolean found = false;
        int meta = 0;
        for (int i = 0; i < block.getVariantCount(); i++) {
            if (block.getVariantSuffix(i)
                .equalsIgnoreCase(variant)) {
                meta = i;
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Variant '" + variant + "' not found for planet " + planet.getName());
        }
        return new BlockMeta(block, meta);
    }
}
