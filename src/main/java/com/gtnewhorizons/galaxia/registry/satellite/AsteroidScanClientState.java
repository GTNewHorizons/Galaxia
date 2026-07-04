package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public final class AsteroidScanClientState {

    private static List<AsteroidSatelliteScanSnapshot> scanSnapshots = List.of();
    private static List<AsteroidSatelliteScanCompletionSnapshot> scanCompletions = List.of();

    private AsteroidScanClientState() {}

    public static List<AsteroidSatelliteScanSnapshot> scanSnapshots() {
        return scanSnapshots;
    }

    public static List<AsteroidSatelliteScanCompletionSnapshot> scanCompletions() {
        return scanCompletions;
    }

    public static Optional<AsteroidSatelliteScanSnapshot> scanSnapshot(CelestialObjectKey key) {
        if (key == null || !key.isMinorBody()) return Optional.empty();
        return scanSnapshots.stream()
            .filter(
                snapshot -> snapshot.asteroidId()
                    .equals(key.minorBodyId()))
            .findFirst();
    }

    public static void updateScans(List<AsteroidSatelliteScanSnapshot> newScanSnapshots,
        List<AsteroidSatelliteScanCompletionSnapshot> newScanCompletions) {
        scanSnapshots = List.copyOf(newScanSnapshots == null ? List.of() : newScanSnapshots);
        scanCompletions = List.copyOf(newScanCompletions == null ? List.of() : newScanCompletions);
    }

    public static void clear() {
        scanSnapshots = List.of();
        scanCompletions = List.of();
    }
}
