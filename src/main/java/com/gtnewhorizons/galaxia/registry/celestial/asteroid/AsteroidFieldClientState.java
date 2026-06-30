package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanCompletionSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;

public final class AsteroidFieldClientState {

    private static List<AsteroidFieldKnowledgeSnapshot> snapshots = List.of();
    private static List<AsteroidSatelliteScanSnapshot> scanSnapshots = List.of();
    private static List<AsteroidSatelliteScanCompletionSnapshot> scanCompletions = List.of();

    private AsteroidFieldClientState() {}

    public static List<AsteroidFieldKnowledgeSnapshot> snapshots() {
        return snapshots;
    }

    public static List<AsteroidSatelliteScanSnapshot> scanSnapshots() {
        return scanSnapshots;
    }

    public static List<AsteroidSatelliteScanCompletionSnapshot> scanCompletions() {
        return scanCompletions;
    }

    public static void update(List<AsteroidFieldKnowledgeSnapshot> newSnapshots) {
        snapshots = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
    }

    public static void updateScans(List<AsteroidSatelliteScanSnapshot> newScanSnapshots,
        List<AsteroidSatelliteScanCompletionSnapshot> newScanCompletions) {
        scanSnapshots = List.copyOf(newScanSnapshots == null ? List.of() : newScanSnapshots);
        scanCompletions = List.copyOf(newScanCompletions == null ? List.of() : newScanCompletions);
    }

    public static Optional<AsteroidOreKnowledgeState> oreKnowledge(CelestialObjectKey key) {
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

    public static Optional<AsteroidDetectionState> detectionState(CelestialObjectKey key) {
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
