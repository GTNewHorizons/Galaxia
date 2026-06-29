package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public final class AsteroidFieldClientState {

    private static List<AsteroidFieldKnowledgeSnapshot> snapshots = List.of();

    private AsteroidFieldClientState() {}

    public static List<AsteroidFieldKnowledgeSnapshot> snapshots() {
        return snapshots;
    }

    public static void update(List<AsteroidFieldKnowledgeSnapshot> newSnapshots) {
        snapshots = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
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

    public static void clear() {
        snapshots = List.of();
    }
}
