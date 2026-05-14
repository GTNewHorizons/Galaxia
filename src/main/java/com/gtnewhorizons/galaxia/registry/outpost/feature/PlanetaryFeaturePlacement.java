package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class PlanetaryFeaturePlacement {

    private static final double MIN_PATCH_AREA = 1.0;

    private final double meanTiles;
    private final double stdDevTiles;
    private final double densityMultiplier;
    private final boolean isolated;

    private PlanetaryFeaturePlacement(double meanTiles, double stdDevTiles, double densityMultiplier,
        boolean isolated) {
        if (meanTiles < MIN_PATCH_AREA) throw new IllegalArgumentException("Feature placement meanTiles must be >= 1");
        if (stdDevTiles < 0.0) throw new IllegalArgumentException("Feature placement stdDevTiles must not be negative");
        this.meanTiles = meanTiles;
        this.stdDevTiles = stdDevTiles;
        this.densityMultiplier = Math.max(0.0, densityMultiplier);
        this.isolated = isolated;
    }

    public static PlanetaryFeaturePlacement patch(double meanTiles, double stdDevTiles) {
        return new PlanetaryFeaturePlacement(meanTiles, stdDevTiles, 1.0, false);
    }

    public static PlanetaryFeaturePlacement clusteredPatch(double meanTiles, double stdDevTiles) {
        return new PlanetaryFeaturePlacement(meanTiles, stdDevTiles, 1.8, false);
    }

    public static PlanetaryFeaturePlacement isolated() {
        return new PlanetaryFeaturePlacement(1.0, 0.0, 2.5, true);
    }

    public boolean isIsolated() {
        return isolated;
    }

    boolean contains(long baseSeed, PlanetaryFeatureKey key, StationTileCoord tile, double featureTileChance,
        double weightShare) {
        Objects.requireNonNull(key, "Planetary feature key must not be null");
        if (tile == null || featureTileChance <= 0.0 || weightShare <= 0.0) return false;
        return contains(baseSeed, key, tile.dx(), tile.dy(), featureTileChance, weightShare);
    }

    boolean contains(long baseSeed, PlanetaryFeatureKey key, int tileX, int tileY, double featureTileChance,
        double weightShare) {
        Objects.requireNonNull(key, "Planetary feature key must not be null");
        if (featureTileChance <= 0.0 || weightShare <= 0.0) return false;
        int maxRadius = maxRadius();
        int cellSize = Math.max(3, maxRadius * 2 + 3);
        double targetCoverage = Math.min(1.0, featureTileChance * weightShare);
        double highArea = Math.max(MIN_PATCH_AREA, meanTiles + stdDevTiles * 0.5);
        double cellArea = cellSize * cellSize;
        double spawnChance = Math.min(1.0, targetCoverage * cellArea / highArea * densityMultiplier);
        int cellX = Math.floorDiv(tileX, cellSize);
        int cellY = Math.floorDiv(tileY, cellSize);
        long featureSeed = featureSeed(baseSeed, key);
        for (int x = cellX - 1; x <= cellX + 1; x++) {
            for (int y = cellY - 1; y <= cellY + 1; y++) {
                long cellSeed = mix(featureSeed ^ ((long) x << 32) ^ (y & 0xffffffffL));
                if (unitDouble(cellSeed) >= spawnChance) continue;
                if (containsGeneratedPatch(cellSeed, tileX, tileY, x, y, cellSize)) return true;
            }
        }
        return false;
    }

    double score(long baseSeed, PlanetaryFeatureKey key, StationTileCoord tile) {
        return score(baseSeed, key, tile.dx(), tile.dy());
    }

    double score(long baseSeed, PlanetaryFeatureKey key, int tileX, int tileY) {
        long featureSeed = featureSeed(baseSeed, key);
        long tileSeed = mix(featureSeed ^ ((long) tileX << 32) ^ (tileY & 0xffffffffL));
        return unitDouble(mix(tileSeed ^ 0x9E3779B97F4A7C15L));
    }

    private boolean containsGeneratedPatch(long cellSeed, int tileX, int tileY, int cellX, int cellY, int cellSize) {
        int centerX = cellX * cellSize + (int) (unitDouble(mix(cellSeed ^ 0xD1B54A32D192ED03L)) * cellSize);
        int centerY = cellY * cellSize + (int) (unitDouble(mix(cellSeed ^ 0xABC98388FB8FAC03L)) * cellSize);
        if (isolated) return tileX == centerX && tileY == centerY;

        double area = sampledArea(cellSeed);
        double aspect = 0.65 + unitDouble(mix(cellSeed ^ 0xDB4F0B9175AE2165L)) * 2.35;
        if ((mix(cellSeed ^ 0xBBE0563303A4615FL) & 1L) == 0L) aspect = 1.0 / aspect;
        double rotation = unitDouble(mix(cellSeed ^ 0xA0F2EC75A1FE1575L)) * Math.PI;
        double major = Math.sqrt(area * Math.max(aspect, 1.0) / Math.PI);
        double minor = Math.sqrt(area / Math.max(aspect, 1.0) / Math.PI);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double dx = tileX - centerX;
        double dy = tileY - centerY;
        double localX = dx * cos + dy * sin;
        double localY = -dx * sin + dy * cos;
        double normalized = (localX * localX) / (major * major) + (localY * localY) / (minor * minor);
        double edgeNoise = 0.75 + unitDouble(mix(cellSeed ^ ((long) tileX << 32) ^ (tileY & 0xffffffffL))) * 0.5;
        return normalized <= edgeNoise;
    }

    private double sampledArea(long seed) {
        double u1 = Math.max(1.0E-9, unitDouble(mix(seed ^ 0x6A09E667F3BCC909L)));
        double u2 = unitDouble(mix(seed ^ 0xBB67AE8584CAA73BL));
        double gaussian = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return Math.max(MIN_PATCH_AREA, meanTiles + gaussian * stdDevTiles);
    }

    private int maxRadius() {
        double highArea = meanTiles + stdDevTiles * 3.0;
        return Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(MIN_PATCH_AREA, highArea))));
    }

    private static long featureSeed(long baseSeed, PlanetaryFeatureKey key) {
        return mix(
            baseSeed ^ key.id()
                .hashCode());
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static double unitDouble(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
