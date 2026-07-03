package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanCompletionSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;

/**
 * Client-side read model for object knowledge synced from the server.
 *
 * Asteroid snapshots are the first producer, but the state is intentionally
 * anchored in the celestial knowledge package so future discoverable moons or
 * surfaces do not need their own static client stores.
 */
public final class CelestialKnowledgeClientState {

    private static List<AsteroidFieldKnowledgeSnapshot> snapshots = List.of();
    private static List<AsteroidSatelliteScanSnapshot> scanSnapshots = List.of();
    private static List<AsteroidSatelliteScanCompletionSnapshot> scanCompletions = List.of();

    private CelestialKnowledgeClientState() {}

    public static List<AsteroidFieldKnowledgeSnapshot> asteroidFieldSnapshots() {
        return snapshots;
    }

    public static List<AsteroidSatelliteScanSnapshot> scanSnapshots() {
        return scanSnapshots;
    }

    public static List<AsteroidSatelliteScanCompletionSnapshot> scanCompletions() {
        return scanCompletions;
    }

    public static void updateAsteroidFields(List<AsteroidFieldKnowledgeSnapshot> newSnapshots) {
        snapshots = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
    }

    public static void updateScans(List<AsteroidSatelliteScanSnapshot> newScanSnapshots,
        List<AsteroidSatelliteScanCompletionSnapshot> newScanCompletions) {
        scanSnapshots = List.copyOf(newScanSnapshots == null ? List.of() : newScanSnapshots);
        scanCompletions = List.copyOf(newScanCompletions == null ? List.of() : newScanCompletions);
    }

    public static Optional<AsteroidOreKnowledgeState> asteroidOreKnowledge(CelestialObjectKey key) {
        if (key == null || !key.isMinorBody()) return Optional.empty();
        for (AsteroidFieldKnowledgeSnapshot snapshot : snapshots) {
            if (snapshot.beltId() != key.minorBodyId()
                .parentBeltId()) continue;
            for (AsteroidFieldKnowledgeSnapshot.Entry entry : snapshot.entries()) {
                if (entry.index() == key.minorBodyId()
                    .index()) return Optional.of(entry.oreKnowledgeState());
            }
        }
        return Optional.empty();
    }

    public static Optional<DiscoveryState> discoveryState(CelestialObjectKey key) {
        if (key == null || !key.isMinorBody()) return Optional.empty();
        for (AsteroidFieldKnowledgeSnapshot snapshot : snapshots) {
            if (snapshot.beltId() != key.minorBodyId()
                .parentBeltId()) continue;
            for (AsteroidFieldKnowledgeSnapshot.Entry entry : snapshot.entries()) {
                if (entry.index() == key.minorBodyId()
                    .index()) return Optional.of(entry.detectionState());
            }
        }
        return Optional.empty();
    }

    public static Optional<AsteroidSatelliteScanSnapshot> scanSnapshot(CelestialObjectKey key) {
        if (key == null || !key.isMinorBody()) return Optional.empty();
        return scanSnapshots.stream()
            .filter(
                snapshot -> snapshot.asteroidId()
                    .equals(key.minorBodyId()))
            .findFirst();
    }

    public static void clear() {
        snapshots = List.of();
        scanSnapshots = List.of();
        scanCompletions = List.of();
    }
}
