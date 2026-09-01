package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public final class CelestialDiscoveryClientState {

    private static List<CelestialDiscoveryScanSnapshot> snapshots = List.of();
    private static int revision;

    private CelestialDiscoveryClientState() {}

    public static List<CelestialDiscoveryScanSnapshot> snapshots() {
        return snapshots;
    }

    /** Bumped whenever the synced scans change, so readers can cache derived views. */
    public static int revision() {
        return revision;
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
        CelestialDiscoveryCapability capability) {
        if (targetKey == null || capability == null) return Optional.empty();
        return snapshots.stream()
            .filter(snapshot -> targetKey.equals(snapshot.targetKey()) && snapshot.capability() == capability)
            .findFirst();
    }

    public static void update(List<CelestialDiscoveryScanSnapshot> newSnapshots) {
        List<CelestialDiscoveryScanSnapshot> updated = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
        if (snapshots.equals(updated)) return;
        snapshots = updated;
        revision++;
    }

    public static void clear() {
        if (snapshots.isEmpty()) return;
        snapshots = List.of();
        revision++;
    }
}
