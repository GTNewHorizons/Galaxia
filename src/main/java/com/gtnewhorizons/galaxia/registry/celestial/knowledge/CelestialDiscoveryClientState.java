package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public final class CelestialDiscoveryClientState {

    private static List<CelestialDiscoveryScanSnapshot> snapshots = List.of();

    private CelestialDiscoveryClientState() {}

    public static List<CelestialDiscoveryScanSnapshot> snapshots() {
        return snapshots;
    }

    public static Optional<CelestialDiscoveryScanSnapshot> scan(CelestialObjectKey anchorKey,
        CelestialDiscoveryCapability capability) {
        if (anchorKey == null || capability == null) return Optional.empty();
        return snapshots.stream()
            .filter(
                snapshot -> snapshot.anchorKey()
                    .equals(anchorKey) && snapshot.capability() == capability)
            .findFirst();
    }

    public static Optional<CelestialDiscoveryScanSnapshot> scanTarget(CelestialObjectKey targetKey,
        SatelliteKind capability) {
        if (targetKey == null || capability == null) return Optional.empty();
        return snapshots.stream()
            .filter(snapshot -> targetKey.equals(snapshot.targetKey()) && snapshot.capability() == capability)
            .findFirst();
    }

    public static void update(List<CelestialDiscoveryScanSnapshot> newSnapshots) {
        snapshots = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
    }

    public static void clear() {
        snapshots = List.of();
    }
}
