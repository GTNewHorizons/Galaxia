package com.gtnewhorizons.galaxia.registry.outpost.feature;

public record FeatureContribution(PlanetaryFeatureKey key, byte coveredTiles, byte totalTiles, String effectLine) {

    public FeatureContribution {
        if (key == null) {
            throw new IllegalArgumentException("Feature contribution key must not be null");
        }
        if (coveredTiles <= 0 || totalTiles <= 0 || coveredTiles > totalTiles) {
            throw new IllegalArgumentException("Invalid feature coverage: " + coveredTiles + "/" + totalTiles);
        }
        effectLine = effectLine == null ? "" : effectLine;
    }
}
