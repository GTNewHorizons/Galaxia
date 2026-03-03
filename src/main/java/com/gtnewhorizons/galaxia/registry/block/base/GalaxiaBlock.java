package com.gtnewhorizons.galaxia.registry.block.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * Registers the given block variants for a given planet, with optional default and per-variant drops
     * Usage: reg(planet, [defaultDrop,] variant1, variant2, ..., [customDrop, variantN], ...)
     * If defaultDrop is provided (as first arg after planet), it applies to all until overridden
     * customDrop can be inserted before a variant to change drop for it and subsequent
     * Use GalaxiaItemList.NULL for "drop self"
     *
     * @param planet The planet intended the block variants is generated on
     * @param args   Varargs of BlockVariant and optional GalaxiaItemList for drops
     */
    public static void reg(DimensionEnum planet, Object... args) {
        List<BlockVariant> variantsList = new ArrayList<>();
        List<Item> dropsList = new ArrayList<>();

        int index = 0;
        GalaxiaItemList defaultDropItem = null;
        if (args.length > 0 && args[0] instanceof GalaxiaItemList) {
            defaultDropItem = (GalaxiaItemList) args[0];
            index = 1;
        }

        Item currentDrop = defaultDropItem == null ? null : defaultDropItem.getItem();

        for (; index < args.length; index++) {
            Object arg = args[index];
            if (arg == null) {
                currentDrop = null;
            } else if (arg instanceof GalaxiaItemList) {
                currentDrop = ((GalaxiaItemList) arg).getItem();
            } else if (arg instanceof BlockVariant) {
                variantsList.add((BlockVariant) arg);
                dropsList.add(currentDrop);
            } else {
                throw new IllegalArgumentException(
                    "Invalid argument type: " + arg.getClass()
                        .getName());
            }
        }

        BlockPlanetGalaxia block = new BlockPlanetGalaxia(
            planet.getName(),
            dropsList.toArray(new Item[0]),
            variantsList.toArray(new BlockVariant[0]));

        GameRegistry.registerBlock(block, ItemBlockGalaxiaPlanet.class, planet.getName());
        planetBlocks.put(planet, block);

        if (defaultDropItem != null) {
            planetDropMap.put(planet, defaultDropItem.getItem());
        }
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
