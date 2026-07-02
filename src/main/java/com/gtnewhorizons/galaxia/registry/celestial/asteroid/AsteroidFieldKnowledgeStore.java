package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/**
 * Team-scoped discovery/prospecting state for asteroid fields.
 *
 * The field profile defines what exists; this store tracks what each team knows
 * about those definitions so scanning can be persisted without mutating content
 * registration.
 */
public final class AsteroidFieldKnowledgeStore {

    private final Map<UUID, Map<CelestialObjectId, AsteroidFieldKnowledge>> knowledgeByTeam = new LinkedHashMap<>();

    public Optional<AsteroidFieldKnowledge> get(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId) {
        return Optional.ofNullable(
            knowledgeByTeam.getOrDefault(teamId, Map.of())
                .get(beltId));
    }

    public AsteroidFieldKnowledge getOrCreate(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return knowledgeByTeam.computeIfAbsent(teamId, key -> new LinkedHashMap<>())
            .computeIfAbsent(beltId, key -> AsteroidFieldKnowledge.initialize(beltId, profile));
    }

    public Optional<AsteroidFieldNode> detectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return detectNext(teamId, beltId, profile, node -> true);
    }

    public Optional<AsteroidFieldNode> detectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope) {
        return detectNext(teamId, beltId, profile, scope, AsteroidFieldScanOrder.byIndex());
    }

    public Optional<AsteroidFieldNode> detectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order) {
        AsteroidFieldKnowledge knowledge = getOrCreate(teamId, beltId, profile);
        Optional<AsteroidFieldNode> candidate = knowledge.nextDetectionCandidate(scope, order);
        candidate.ifPresent(node -> knowledge.detect(node.id()));
        return candidate;
    }

    public Optional<AsteroidFieldNode> prospectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return prospectNext(teamId, beltId, profile, node -> true);
    }

    public Optional<AsteroidFieldNode> prospectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope) {
        return prospectNext(teamId, beltId, profile, scope, AsteroidFieldScanOrder.byIndex());
    }

    public Optional<AsteroidFieldNode> prospectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order) {
        AsteroidFieldKnowledge knowledge = getOrCreate(teamId, beltId, profile);
        Optional<AsteroidFieldNode> candidate = knowledge.nextProspectingCandidate(scope, order);
        candidate.ifPresent(node -> knowledge.prospect(node.id(), scope));
        return candidate;
    }

    public List<AsteroidFieldKnowledgeSnapshot> snapshots(@Nonnull UUID teamId) {
        Map<CelestialObjectId, AsteroidFieldKnowledge> teamKnowledge = knowledgeByTeam.get(teamId);
        if (teamKnowledge == null || teamKnowledge.isEmpty()) return List.of();
        return teamKnowledge.entrySet()
            .stream()
            .map(
                entry -> entry.getValue()
                    .snapshot(entry.getKey()))
            .toList();
    }

    public Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> snapshotsByTeam() {
        Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> snapshots = new LinkedHashMap<>();
        for (UUID teamId : knowledgeByTeam.keySet()) {
            snapshots.put(teamId, snapshots(teamId));
        }
        return Map.copyOf(snapshots);
    }

    public void restore(@Nonnull UUID teamId, @Nonnull List<AsteroidFieldKnowledgeSnapshot> snapshots,
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        Map<CelestialObjectId, AsteroidFieldKnowledge> restored = new LinkedHashMap<>();
        // Restore validates the current profile instead of trusting the snapshot
        // blindly. A snapshot without registered content would otherwise create
        // knowledge for a belt the game can no longer resolve.
        for (AsteroidFieldKnowledgeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshot cannot be null");
            }
            Optional<AsteroidFieldProfile> resolvedProfile = profileResolver.apply(snapshot.beltId());
            if (resolvedProfile == null) {
                throw new IllegalStateException("profileResolver cannot return null");
            }
            AsteroidFieldProfile profile = resolvedProfile
                .orElseThrow(() -> new IllegalStateException("No asteroid field profile for " + snapshot.beltId()));
            if (restored.containsKey(snapshot.beltId())) {
                throw new IllegalStateException("Duplicate asteroid snapshot for belt " + snapshot.beltId());
            }
            restored.put(snapshot.beltId(), AsteroidFieldKnowledge.fromSnapshot(snapshot.beltId(), profile, snapshot));
        }
        if (restored.isEmpty()) {
            knowledgeByTeam.remove(teamId);
        } else {
            knowledgeByTeam.put(teamId, restored);
        }
    }

    public void clear() {
        knowledgeByTeam.clear();
    }
}
