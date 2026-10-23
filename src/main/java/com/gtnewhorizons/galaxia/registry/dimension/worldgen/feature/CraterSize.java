package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Locale;
import java.util.Random;

/** Initial lunar tuning, subject to review against generated terrain in game. */
public enum CraterSize {

    SMALL(16, 3, 60),
    MEDIUM(48, 9, 30),
    LARGE(96, 24, 10);

    public static final int MAX_DIAMETER = 256;
    public static final int GENERATION_RARITY = 32;

    public final int meanDiameter;
    public final int diameterDeviation;
    public final int weight;

    CraterSize(int meanDiameter, int diameterDeviation, int weight) {
        this.meanDiameter = meanDiameter;
        this.diameterDeviation = diameterDeviation;
        this.weight = weight;
    }

    public int sampleDiameter(Random random) {
        return Math
            .min(MAX_DIAMETER, Math.max(2, (int) Math.round(meanDiameter + random.nextGaussian() * diameterDeviation)));
    }

    public String translationKey() {
        return "galaxia.crater.size." + name().toLowerCase(Locale.ROOT);
    }

    public static CraterSize select(Random random) {
        int roll = random.nextInt(100);
        for (CraterSize size : values()) {
            roll -= size.weight;
            if (roll < 0) return size;
        }
        throw new IllegalStateException("Crater size weights must sum to 100");
    }
}
