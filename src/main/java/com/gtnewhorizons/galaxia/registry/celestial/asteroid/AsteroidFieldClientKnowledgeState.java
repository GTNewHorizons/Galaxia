package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public final class AsteroidFieldClientKnowledgeState {

    private static List<AsteroidFieldKnowledgeSnapshot> snapshots = List.of();
    private static Map<CelestialObjectKey, CelestialResourceKnowledgeState> oreKnowledgeByKey = Map.of();

    private AsteroidFieldClientKnowledgeState() {}

    public static List<AsteroidFieldKnowledgeSnapshot> snapshots() {
        return snapshots;
    }

    public static Optional<CelestialResourceKnowledgeState> oreKnowledge(CelestialObjectKey key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(oreKnowledgeByKey.get(key));
    }

    public static void updateFields(List<AsteroidFieldKnowledgeSnapshot> newSnapshots) {
        snapshots = List.copyOf(newSnapshots == null ? List.of() : newSnapshots);
        Map<CelestialObjectKey, DiscoveryState> discoveryByKey = new LinkedHashMap<>();
        Map<CelestialObjectKey, CelestialResourceKnowledgeState> oreByKey = new LinkedHashMap<>();
        for (AsteroidFieldKnowledgeSnapshot snapshot : snapshots) {
            for (AsteroidFieldKnowledgeSnapshot.Entry entry : snapshot.entries()) {
                CelestialObjectKey key = CelestialObjectKey
                    .minorBody(new MinorCelestialBodyId(snapshot.beltId(), entry.index()));
                discoveryByKey.put(key, entry.detectionState());
                oreByKey.put(key, entry.oreKnowledgeState());
            }
        }
        oreKnowledgeByKey = Map.copyOf(oreByKey);
        CelestialKnowledgeClientState.updateDiscoverySource(AsteroidFieldClientKnowledgeState.class, discoveryByKey);
    }

    public static void clear() {
        snapshots = List.of();
        oreKnowledgeByKey = Map.of();
        CelestialKnowledgeClientState.updateDiscoverySource(AsteroidFieldClientKnowledgeState.class, Map.of());
    }
}
