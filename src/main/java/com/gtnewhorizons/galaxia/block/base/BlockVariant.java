package com.gtnewhorizons.galaxia.block.base;

import com.github.bsideup.jabel.Desugar;

/**
 * Defines the different variants of a block, and the characteristics they should have
 */
@Desugar
public record BlockVariant(String suffix, float hardness, boolean falling, String harvestTool, int harvestLevel,
    boolean dropsDust) {

    /**
     * Registers the block variant as having similar characteristics to vanilla stone
     *
     * @param suffix   The suffix in the ENUM giving the type of rock, e.g. Andesite etc.
     * @param hardness The desired hardness level of the block
     * @return A BlockVariant with desired characteristics
     */
    public static BlockVariant stoneLike(String suffix, float hardness) {
        return new BlockVariant(suffix, hardness, false, "pickaxe", 1, true);
    }

    /**
     * Registers the block variant as having similar characteristics to vanilla sand
     *
     * @param suffix   The suffix in the ENUM giving the type of rock, e.g. Regolith etc.
     * @param hardness The desired hardness level of the block
     * @return A BlockVariant with desired characteristics
     */
    public static BlockVariant sandLike(String suffix, float hardness) {
        return new BlockVariant(suffix, hardness, true, "shovel", 0, true);
    }

    /**
     * Registers the block variant as having similar characteristics to vanilla dirt
     *
     * @param suffix   The suffix in the ENUM giving the type of rock, e.g. Regolith etc.
     * @param hardness The desired hardness level of the block
     * @return A BlockVariant with desired characteristics
     */
    public static BlockVariant dirtLike(String suffix, float hardness) {
        return new BlockVariant(suffix, hardness, false, "shovel", 0, true);
    }

    // Creating block variants for all ENUM requirements
    public static final BlockVariant REGOLITH = sandLike("regolith", 0.7F);
    public static final BlockVariant ANDESITE = stoneLike("andesite", 2F);
    public static final BlockVariant ANORTHOSITE = stoneLike("anorthosite", 2F);
    public static final BlockVariant BASALT = stoneLike("basalt", 1.5F);
    public static final BlockVariant BRECCIA = stoneLike("breccia", 2F);
    public static final BlockVariant GABBRO = stoneLike("gabbro", 2F);
    public static final BlockVariant MAGMA = stoneLike("magma", 8F);
    public static final BlockVariant TEKTITE = stoneLike("tektite", 10F);
    public static final BlockVariant SNOW = dirtLike("snow", 0.5F);
    public static final BlockVariant ICE = stoneLike("ice", 1.0F);
}
