package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class PlanetarySatelliteStore {

    public static final PlanetarySatelliteStore SERVER = new PlanetarySatelliteStore();
    public static final PlanetarySatelliteStore CLIENT = new PlanetarySatelliteStore();

    private final Map<UUID, Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>>> counts = new LinkedHashMap<>();

    public int count(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        validateKey(teamId, bodyId, kind);
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> teamCounts = counts.get(teamId);
        if (teamCounts == null) return 0;
        EnumMap<SatelliteKind, Integer> bodyCounts = teamCounts.get(bodyId);
        if (bodyCounts == null) return 0;
        return bodyCounts.getOrDefault(kind, 0);
    }

    public long bandwidth(UUID teamId, CelestialObjectId bodyId) {
        validateBody(teamId, bodyId);
        long total = 0L;
        for (SatelliteKind kind : SatelliteKind.values()) {
            total += (long) count(teamId, bodyId, kind) * kind.bandwidthPerSatellite();
        }
        return total;
    }

    public double miningSpeedBonus(UUID teamId, CelestialObjectId bodyId) {
        validateBody(teamId, bodyId);
        double total = 0.0D;
        for (SatelliteKind kind : SatelliteKind.values()) {
            total += count(teamId, bodyId, kind) * kind.miningSpeedBonusPerSatellite();
        }
        return total;
    }

    public void add(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind, int amount) {
        validateKey(teamId, bodyId, kind);
        if (amount < 0) throw new IllegalArgumentException("Satellite add amount must be non-negative: " + amount);
        int current = count(teamId, bodyId, kind);
        if (Integer.MAX_VALUE - current < amount) {
            throw new IllegalArgumentException(
                "Satellite count overflow for team " + teamId + ", body " + bodyId + ", kind " + kind);
        }
        set(teamId, bodyId, kind, current + amount);
    }

    public void set(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind, int count) {
        validateKey(teamId, bodyId, kind);
        if (count < 0) throw new IllegalArgumentException("Satellite count must be non-negative: " + count);
        if (count == 0) {
            deleteAll(teamId, bodyId, kind);
            return;
        }
        counts.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(bodyId, ignored -> new EnumMap<>(SatelliteKind.class))
            .put(kind, count);
    }

    public void deleteAll(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        validateKey(teamId, bodyId, kind);
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> teamCounts = counts.get(teamId);
        if (teamCounts == null) return;
        EnumMap<SatelliteKind, Integer> bodyCounts = teamCounts.get(bodyId);
        if (bodyCounts == null) return;
        bodyCounts.remove(kind);
        if (bodyCounts.isEmpty()) teamCounts.remove(bodyId);
        if (teamCounts.isEmpty()) counts.remove(teamId);
    }

    public void clear() {
        counts.clear();
    }

    public Map<UUID, Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>>> snapshot() {
        Map<UUID, Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>>> teamEntry : counts.entrySet()) {
            Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> bodySnapshot = new LinkedHashMap<>();
            for (Map.Entry<CelestialObjectId, EnumMap<SatelliteKind, Integer>> bodyEntry : teamEntry.getValue()
                .entrySet()) {
                bodySnapshot.put(bodyEntry.getKey(), new EnumMap<>(bodyEntry.getValue()));
            }
            snapshot.put(teamEntry.getKey(), bodySnapshot);
        }
        return snapshot;
    }

    public Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> snapshotTeam(UUID teamId) {
        if (teamId == null) throw new IllegalArgumentException("Satellite team id is required");
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> teamCounts = counts.get(teamId);
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> snapshot = new LinkedHashMap<>();
        if (teamCounts == null) return snapshot;
        for (Map.Entry<CelestialObjectId, EnumMap<SatelliteKind, Integer>> bodyEntry : teamCounts.entrySet()) {
            snapshot.put(bodyEntry.getKey(), new EnumMap<>(bodyEntry.getValue()));
        }
        return snapshot;
    }

    public void replaceTeam(UUID teamId, Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> replacement) {
        if (teamId == null) throw new IllegalArgumentException("Satellite team id is required");
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> current = counts.remove(teamId);
        if (current != null) current.clear();
        if (replacement == null || replacement.isEmpty()) return;
        for (Map.Entry<CelestialObjectId, EnumMap<SatelliteKind, Integer>> bodyEntry : replacement.entrySet()) {
            CelestialObjectId bodyId = bodyEntry.getKey();
            if (bodyEntry.getValue() == null) continue;
            for (Map.Entry<SatelliteKind, Integer> kindEntry : bodyEntry.getValue()
                .entrySet()) {
                Integer count = kindEntry.getValue();
                if (count == null || count <= 0) continue;
                set(teamId, bodyId, kindEntry.getKey(), count);
            }
        }
    }

    private static void validateKey(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        validateBody(teamId, bodyId);
        if (kind == null) throw new IllegalArgumentException("Satellite kind is required");
    }

    private static void validateBody(UUID teamId, CelestialObjectId bodyId) {
        if (teamId == null) throw new IllegalArgumentException("Satellite team id is required");
        if (bodyId == null) throw new IllegalArgumentException("Satellite body id is required");
        if (bodyId == CelestialObjectId.INVALID) throw new IllegalArgumentException("Satellite body id is invalid");
    }
}
