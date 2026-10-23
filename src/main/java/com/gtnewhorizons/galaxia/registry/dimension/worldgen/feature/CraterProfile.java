package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

/** Shared content definition for generation, extraction, prospecting and NEI. */
public enum CraterProfile {

    IRON_SILICA("iron_silica", new int[] { 50, 60, 70 }, 2000, 2),
    GOLD_COBALT("gold_cobalt", new int[] { 20, 20, 18 }, 1000, 4),
    SCANDIUM_BAUXITE("scandium_bauxite", new int[] { 25, 16, 10 }, 500, 8),
    PLATINUM("platinum", new int[] { 5, 4, 2 }, 250, 16);

    public final String id;
    private final int[] sizeWeights;
    private final int smallReserve;
    private final int fluidGrowthPerPortion;

    CraterProfile(String id, int[] sizeWeights, int smallReserve, int fluidGrowthPerPortion) {
        this.id = id;
        this.sizeWeights = sizeWeights;
        this.smallReserve = smallReserve;
        this.fluidGrowthPerPortion = fluidGrowthPerPortion;
    }

    public int weight(CraterSize size) {
        return sizeWeights[size.ordinal()];
    }

    public long initialReserve(CraterSize size, int diameter, Random random) {
        if (diameter <= 0) throw new IllegalArgumentException("Crater diameter must be positive");
        double nominal = smallReserve * (double) size.meanDiameter / CraterSize.SMALL.meanDiameter;
        double richness = diameter / (double) size.meanDiameter;
        return Math.max(1, Math.round(nominal * richness * (0.9 + random.nextDouble() * 0.2)));
    }

    public long drillingFluidPerOperation(long completedFrackingPortions) {
        if (completedFrackingPortions < 0) throw new IllegalArgumentException("Negative fracking history");
        return Math.addExact(16000L, Math.multiplyExact(completedFrackingPortions, fluidGrowthPerPortion));
    }

    public static CraterProfile select(CraterSize size, Random random) {
        int roll = random.nextInt(100);
        for (CraterProfile profile : values()) {
            roll -= profile.weight(size);
            if (roll < 0) return profile;
        }
        throw new IllegalStateException("Crater profile weights must sum to 100");
    }
}
