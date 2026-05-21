package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Objects;
import java.util.function.Consumer;

import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModuleAreaEffect {

    private final int radius;
    private final int upkeepMultiplierPercent;

    private ModuleAreaEffect(int radius, int upkeepMultiplierPercent) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1");
        }
        if (upkeepMultiplierPercent < 0) {
            throw new IllegalArgumentException("upkeepMultiplierPercent must not be negative");
        }
        this.radius = radius;
        this.upkeepMultiplierPercent = upkeepMultiplierPercent;
    }

    public static ModuleAreaEffect adjacentUpkeepMultiplier(int upkeepMultiplierPercent) {
        return new ModuleAreaEffect(1, upkeepMultiplierPercent);
    }

    public void apply(ModuleInstance source, ModuleInstance target, ModuleFeatureModifierBuilder builder) {
        if (!affects(source, target)) return;
        Objects.requireNonNull(builder, "builder");
        builder.minUpkeepMultiplierPercent(upkeepMultiplierPercent);
    }

    public void collectAffectedTiles(ModuleInstance source, Consumer<StationTileCoord> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (source == null || !source.enabled() || source.anchorOrNull() == null) return;
        StationTileCoord[] sourceTiles = source.shape()
            .tiles(source.anchor());
        for (StationTileCoord sourceTile : sourceTiles) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int x = sourceTile.dx() + dx;
                    int y = sourceTile.dy() + dy;
                    if (x < StationTileCoord.MIN || x > StationTileCoord.MAX
                        || y < StationTileCoord.MIN
                        || y > StationTileCoord.MAX) {
                        continue;
                    }
                    StationTileCoord affected = StationTileCoord.of(x, y);
                    if (!contains(sourceTiles, affected)) {
                        consumer.accept(affected);
                    }
                }
            }
        }
    }

    private boolean affects(ModuleInstance source, ModuleInstance target) {
        if (source == null || target == null || source.id.equals(target.id) || !source.enabled()) return false;
        if (source.anchorOrNull() == null || target.anchorOrNull() == null) return false;
        StationTileCoord[] sourceTiles = source.shape()
            .tiles(source.anchor());
        for (StationTileCoord targetTile : target.shape()
            .tiles(target.anchor())) {
            if (contains(sourceTiles, targetTile)) continue;
            for (StationTileCoord sourceTile : sourceTiles) {
                if (Math.abs(targetTile.dx() - sourceTile.dx()) <= radius
                    && Math.abs(targetTile.dy() - sourceTile.dy()) <= radius) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(StationTileCoord[] tiles, StationTileCoord tile) {
        for (StationTileCoord candidate : tiles) {
            if (candidate.equals(tile)) return true;
        }
        return false;
    }
}
