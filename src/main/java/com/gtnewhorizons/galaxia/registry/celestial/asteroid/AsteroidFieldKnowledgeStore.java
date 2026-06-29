package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class AsteroidFieldKnowledgeStore {

    private final Map<UUID, Map<CelestialObjectId, AsteroidFieldKnowledge>> knowledgeByTeam = new LinkedHashMap<>();

    public Optional<AsteroidFieldKnowledge> get(UUID teamId, CelestialObjectId beltId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(beltId, "beltId cannot be null");
        return Optional.ofNullable(
            knowledgeByTeam.getOrDefault(teamId, Map.of())
                .get(beltId));
    }

    public AsteroidFieldKnowledge getOrCreate(UUID teamId, CelestialObjectId beltId, AsteroidFieldProfile profile) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        return knowledgeByTeam.computeIfAbsent(teamId, key -> new LinkedHashMap<>())
            .computeIfAbsent(beltId, key -> AsteroidFieldKnowledge.initialize(beltId, profile));
    }

    public Optional<AsteroidFieldNode> detectNext(UUID teamId, CelestialObjectId beltId, AsteroidFieldProfile profile) {
        AsteroidFieldKnowledge knowledge = getOrCreate(teamId, beltId, profile);
        Optional<AsteroidFieldNode> candidate = knowledge.nextDetectionCandidate();
        candidate.ifPresent(node -> knowledge.detect(node.id()));
        return candidate;
    }

    public Optional<AsteroidFieldNode> prospectNext(UUID teamId, CelestialObjectId beltId,
        AsteroidFieldProfile profile) {
        AsteroidFieldKnowledge knowledge = getOrCreate(teamId, beltId, profile);
        Optional<AsteroidFieldNode> candidate = knowledge.nextProspectingCandidate();
        candidate.ifPresent(node -> knowledge.prospect(node.id()));
        return candidate;
    }

    public void clear() {
        knowledgeByTeam.clear();
    }
}
