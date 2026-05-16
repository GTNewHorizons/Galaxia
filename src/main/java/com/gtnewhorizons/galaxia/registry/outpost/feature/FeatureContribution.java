package com.gtnewhorizons.galaxia.registry.outpost.feature;

public record FeatureContribution(PlanetaryFeatureKey key, byte coveredTiles, byte totalTiles, String effectLine) {

    public static final int STABLE_BEDROCK_UPKEEP_REDUCTION_PERCENT_PER_TILE = 5;
    public static final int REGOLITH_FLATS_BUILD_SPEEDUP_PERCENT_PER_TILE = 20;
    public static final int STABLE_BEDROCK_BUILD_SLOWDOWN_PERCENT_PER_TILE = 20;

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
                "Build speed +" + coveredTiles * REGOLITH_FLATS_BUILD_SPEEDUP_PERCENT_PER_TILE + "%");
        }
        if (PlanetaryFeatureRegistry.STABLE_BEDROCK.key()
            .equals(key)) {
            return new FeatureContribution(
                key,
                (byte) coveredTiles,
                (byte) totalTiles,
                "Upkeep -" + coveredTiles * STABLE_BEDROCK_UPKEEP_REDUCTION_PERCENT_PER_TILE
                    + "%, build speed -"
                    + coveredTiles * STABLE_BEDROCK_BUILD_SLOWDOWN_PERCENT_PER_TILE
                    + "%");
        }
        return null;
    }
}
