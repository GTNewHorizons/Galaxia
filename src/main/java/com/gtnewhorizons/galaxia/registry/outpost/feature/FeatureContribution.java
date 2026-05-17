package com.gtnewhorizons.galaxia.registry.outpost.feature;

public record FeatureContribution(PlanetaryFeatureKey key, byte coveredTiles, byte totalTiles, String effectLine) {

    public static final int STABLE_BEDROCK_UPKEEP_MULTIPLIER_PERCENT = 80;
    public static final int REGOLITH_FLATS_BUILD_SPEEDUP_PERCENT = 20;
    public static final int STABLE_BEDROCK_BUILD_SLOWDOWN_PERCENT = 20;

    public FeatureContribution {
        if (key == null) {
            throw new IllegalArgumentException("Feature contribution key must not be null");
        }
        if (coveredTiles <= 0 || totalTiles <= 0 || coveredTiles > totalTiles) {
            throw new IllegalArgumentException("Invalid feature coverage: " + coveredTiles + "/" + totalTiles);
        }
        effectLine = effectLine == null ? "" : effectLine;
    }

    public static FeatureContribution generic(PlanetaryFeatureKey key, int coveredTiles, int totalTiles) {
        if (PlanetaryFeatureRegistry.REGOLITH_FLATS.key()
            .equals(key)) {
            return new FeatureContribution(
                key,
                (byte) coveredTiles,
                (byte) totalTiles,
                "Build speed +" + REGOLITH_FLATS_BUILD_SPEEDUP_PERCENT + "%");
        }
        if (PlanetaryFeatureRegistry.STABLE_BEDROCK.key()
            .equals(key)) {
            return new FeatureContribution(
                key,
                (byte) coveredTiles,
                (byte) totalTiles,
                "Upkeep x0.8, build speed -" + STABLE_BEDROCK_BUILD_SLOWDOWN_PERCENT + "%");
        }
        return null;
    }
}
