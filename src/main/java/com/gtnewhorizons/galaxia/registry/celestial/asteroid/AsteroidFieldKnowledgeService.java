package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

public final class AsteroidFieldKnowledgeService {

    private static final AsteroidFieldKnowledgeStore STORE = new AsteroidFieldKnowledgeStore();

    private AsteroidFieldKnowledgeService() {}

    public static AsteroidFieldKnowledgeStore store() {
        return STORE;
    }

    public static boolean isDetected(UUID teamId, MinorCelestialBodyId minorBodyId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(minorBodyId, "minorBodyId cannot be null");
        AsteroidFieldProfile profile = profile(minorBodyId.parentBeltId()).orElse(null);
        if (profile == null || !profile.hasNodeIndex(minorBodyId.index())) return false;

        Optional<AsteroidFieldKnowledge> knowledge = STORE.get(teamId, minorBodyId.parentBeltId());
        if (knowledge.isPresent()) {
            return knowledge.get()
                .entryFor(minorBodyId)
                .detectionState() == AsteroidDetectionState.DETECTED;
        }

        AsteroidFieldNode node = AsteroidFieldResolver
            .resolveNode(minorBodyId.parentBeltId(), profile, minorBodyId.index());
        return AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.DETECTED;
    }

    public static List<AsteroidFieldKnowledgeSnapshot> snapshots(UUID teamId) {
        return STORE.snapshots(teamId);
    }

    public static Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> snapshotsByTeam() {
        return STORE.snapshotsByTeam();
    }

    public static void restore(UUID teamId, List<AsteroidFieldKnowledgeSnapshot> snapshots,
        Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        STORE.restore(teamId, snapshots, profileResolver);
    }

    public static void clear() {
        STORE.clear();
    }

    private static Optional<AsteroidFieldProfile> profile(CelestialObjectId beltId) {
        return CelestialRegistry.get(beltId)
            .map(CelestialObject::properties)
            .map(properties -> properties.asteroidFieldProfile());
    }
}
