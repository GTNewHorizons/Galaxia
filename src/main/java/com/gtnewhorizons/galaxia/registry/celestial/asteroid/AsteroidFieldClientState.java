package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;

public final class AsteroidFieldClientState {

    private static List<AsteroidFieldKnowledgeSnapshot> snapshots = List.of();

    private AsteroidFieldClientState() {}

    public static List<AsteroidFieldKnowledgeSnapshot> snapshots() {
        return snapshots;
    }

    public static void update(List<AsteroidFieldKnowledgeSnapshot> newSnapshots) {
        snapshots = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
    }

    public static void clear() {
        snapshots = List.of();
    }
}
