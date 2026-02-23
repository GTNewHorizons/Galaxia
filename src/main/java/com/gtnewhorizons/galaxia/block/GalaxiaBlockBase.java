package com.gtnewhorizons.galaxia.block;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The basic base that all Galaxia Blocks/Variants follow
 */
public class GalaxiaBlockBase {

    private static final Map<DimensionEnum, BlockPlanetGalaxia> planetBlocks = new HashMap<>();
    private static final Map<DimensionEnum, Item> planetDustMap = new HashMap<>();

    /**
     * Registers the given block variants for a given planet
     * 
     * @param planet   The planet intended to generate the block variants
     * @param variants The BlockVariants to register
     */
    static void reg(DimensionEnum planet, BlockVariant... variants) {
        // Ensure there are actual variants
        if (variants.length == 0) {
            throw new IllegalArgumentException("Invalid variant count for " + planet.getName());
        }

        // Registers the blocks variants, and adds to planet hashmap
        BlockPlanetGalaxia block = new BlockPlanetGalaxia(planet.getName(), variants);
        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());
        planetBlocks.put(planet, block);
    }

    /**
     * Registers the given block variants and dust item for a given planet
     * 
     * @param planet   The planet intended to generate the dust items
     * @param dustEnum The ENUM for the dust being registered
     * @param variants The BlockVariants to register
     */
    static void reg(DimensionEnum planet, GalaxiaItemList dustEnum, BlockVariant... variants) {
        // Ensure there are actual variants
        if (variants.length == 0) {
            throw new IllegalArgumentException("Invalid variant count for " + planet.getName());
        }

        // Registers the blocks variants and dust item, and adds to planet hashmaps
        Item dustItem = dustEnum.getItem();
        BlockPlanetGalaxia block = new BlockPlanetGalaxia(planet.getName(), dustItem, variants);
        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());

        planetBlocks.put(planet, block);
        planetDustMap.put(planet, dustItem);
    }

    public static Block get(DimensionEnum planet) {
        return planetBlocks.get(planet);
    }

    /**
     * Returns the BlockMeta for a given variant of the planet blocks
     * 
     * @param planet  The planet from which the blocks generate
     * @param variant The specific variant to get the meta of
     * @return The BlockMeta of the variant
     */
    public static BlockMeta get(DimensionEnum planet, String variant) {
        // Check that the given planet exists and has blocks to generate
        BlockPlanetGalaxia block = planetBlocks.get(planet);
        if (block == null) {
            throw new IllegalArgumentException("Planet not registered: " + planet);
        }

        // Check that the variant exists, and return the BlockMeta
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
