package com.gtnewhorizons.galaxia.block;

public class BlockVariant {

    public final String suffix;
    public final float hardness;
    public final boolean falling;

    private BlockVariant(String suffix, float hardness, boolean falling) {
        this.suffix = suffix;
        this.hardness = hardness;
        this.falling = falling;
    }

    public static final BlockVariant ROCK = new BlockVariant("rock", 1.5F, false);
    public static final BlockVariant SAND = new BlockVariant("sand", 0.8F, true);
    public static final BlockVariant REGOLITH = new BlockVariant("regolith", 1.0F, true);
    public static final BlockVariant ASH = new BlockVariant("ash", 0.6F, true);
}
