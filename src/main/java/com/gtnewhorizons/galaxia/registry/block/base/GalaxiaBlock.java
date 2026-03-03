package com.gtnewhorizons.galaxia.registry.block.base;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.registry.block.planet.BlockPlanetGalaxia;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The basic base that all Galaxia Blocks/Variants follow
 */
public class GalaxiaBlock {

    private static final Map<DimensionEnum, BlockPlanetGalaxia> planetBlocks = new HashMap<>();
    private static final Map<DimensionEnum, Item> planetDropMap = new HashMap<>();

    /**
     * Registers the given block variants for a given planet
     *
     * @param planet   The planet intended to generate the block variants
     * @param variants The BlockVariants to register
     */
    public static void reg(DimensionEnum planet, BlockVariant... variants) {
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
     * @param dropItem The ENUM for the drop item being registered
     * @param variants The BlockVariants to register
     */
    public static void reg(DimensionEnum planet, GalaxiaItemList dropItem, BlockVariant... variants) {
        // Ensure there are actual variants
        if (variants.length == 0) {
            throw new IllegalArgumentException("Invalid variant count for " + planet.getName());
        }

        // Registers the blocks variants and dust item, and adds to planet hashmaps
        Item dustItem = dropItem.getItem();
        BlockPlanetGalaxia block = new BlockPlanetGalaxia(planet.getName(), dustItem, variants);
        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());

        planetBlocks.put(planet, block);
        planetDropMap.put(planet, dustItem);
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
        BlockPlanetGalaxia block = planetBlocks.get(planet);

        for (int meta = 0; meta < block.getVariantCount(); meta++) {
            if (block.getVariantSuffix(meta)
                .equalsIgnoreCase(variant)) {
                return new BlockMeta(block, meta);
            }
        }
        throw new IllegalArgumentException(
            String.format("Variant '%s' not found for planet %s", variant, planet.getName()));
    }

    /**
     * Returns the BlockMeta for a given planet and variant
     *
     * @param planet  The planet from which the blocks generate
     * @param variant The specific BlockVariant instance
     * @return The BlockMeta corresponding to the variant
     */
    public static BlockMeta get(DimensionEnum planet, BlockVariant variant) {
        return get(planet, variant.suffix());
    }

    /**
     * Returns the metadata value for a given planet and variant
     *
     * @param planet  The planet from which the blocks generate
     * @param variant The specific BlockVariant instance
     * @return The metadata value of the variant
     */
    public static int getMeta(DimensionEnum planet, BlockVariant variant) {
        return get(planet, variant).meta();
    }

    /**
     * Returns the Block instance for a given planet and variant enum.
     *
     * @param planet  The planet from which the blocks generate
     * @param variant The specific BlockVariant instance
     * @return The BlockPlanetGalaxia instance for the planet
     */
    public static Block getBlock(DimensionEnum planet, BlockVariant variant) {
        return get(planet, variant).block();
    }
}
