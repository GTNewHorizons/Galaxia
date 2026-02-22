package com.gtnewhorizons.galaxia.block;

public class BlockVariant {

    public final String suffix;
    public final float hardness;
    public final boolean falling;
    public final String harvestTool;
    public final int harvestLevel;
    public final boolean dropsDust;

    private BlockVariant(String suffix, float hardness, boolean falling, String harvestTool, int harvestLevel,
        boolean dropsDust) {
        this.suffix = suffix;
        this.hardness = hardness;
        this.falling = falling;
        this.harvestTool = harvestTool;
        this.harvestLevel = harvestLevel;
        this.dropsDust = dropsDust;
    }

    public static BlockVariant stoneLike(String suffix, float hardness) {
        return new BlockVariant(suffix, hardness, false, "pickaxe", 1, true);
    }

    public static BlockVariant sandLike(String suffix, float hardness) {
        return new BlockVariant(suffix, hardness, true, "shovel", 0, true);
    }

    public static BlockVariant dirtLike(String suffix, float hardness) {
        return new BlockVariant(suffix, hardness, false, "shovel", 0, true);
    }

    public static final BlockVariant REGOLITH = sandLike("regolith", 0.7F);
    public static final BlockVariant ANDESITE = stoneLike("andesite", 2F);
    public static final BlockVariant ANORTHOSITE = stoneLike("anorthosite", 2F);
    public static final BlockVariant BASALT = stoneLike("basalt", 1.5F);
    public static final BlockVariant BRECCIA = stoneLike("breccia", 2F);
    public static final BlockVariant GABBRO = stoneLike("gabbro", 2F);
    public static final BlockVariant MAGMA = stoneLike("magma", 8F);
    public static final BlockVariant TEKTITE = stoneLike("tektite", 10F);
    public static final BlockVariant SNOW = dirtLike("snow", 0.5F);
}
