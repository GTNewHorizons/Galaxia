package com.gtnewhorizons.galaxia.registry.outpost;

/**
 * Immutable logistics configuration for a single resource in a single outpost.
 *
 * <ul>
 * <li>{@code minReserve} – "Keep at least X": the outpost will not supply below this threshold.</li>
 * <li>{@code orderSize} – "Order packets of X": the size of each incoming shipment requested.</li>
 * <li>{@code isImportEnabled} – whether this outpost will request items when stock falls below reserve.</li>
 * <li>{@code isSupplyEnabled} – whether this outpost may export surplus items above the reserve.</li>
 * </ul>
 */

public record LogisticsResourceConfig(int minReserve, int orderSize, boolean isImportEnabled, boolean isSupplyEnabled) {

    public static final LogisticsResourceConfig DEFAULT = new LogisticsResourceConfig(0, 1, false, false);

    public LogisticsResourceConfig {
        if (minReserve < 0) throw new IllegalArgumentException("minReserve must be >= 0");
        if (orderSize <= 0) throw new IllegalArgumentException("orderSize must be > 0");
    }

    public LogisticsResourceConfig withMinReserve(int minReserve) {
        return new LogisticsResourceConfig(minReserve, orderSize, isImportEnabled, isSupplyEnabled);
    }

    public LogisticsResourceConfig withOrderSize(int orderSize) {
        return new LogisticsResourceConfig(minReserve, orderSize, isImportEnabled, isSupplyEnabled);
    }

    public LogisticsResourceConfig withImportEnabled(boolean isImportEnabled) {
        return new LogisticsResourceConfig(minReserve, orderSize, isImportEnabled, isSupplyEnabled);
    }

    public LogisticsResourceConfig withSupplyEnabled(boolean isSupplyEnabled) {
        return new LogisticsResourceConfig(minReserve, orderSize, isImportEnabled, isSupplyEnabled);
    }
}
