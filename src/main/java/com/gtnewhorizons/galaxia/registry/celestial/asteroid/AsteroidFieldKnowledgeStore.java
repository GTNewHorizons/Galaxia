package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryDomain;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeProvider;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

/**
 * Team-scoped discovery/prospecting state for asteroid fields.
 *
 * The field profile defines what exists; this store tracks what each team knows
 * about those definitions so scanning can be persisted without mutating content
 * registration.
 */
public final class AsteroidFieldKnowledgeStore {

    private static final AsteroidFieldKnowledgeStore GLOBAL = new AsteroidFieldKnowledgeStore();
    private static final CelestialKnowledgeProvider PROVIDER = GLOBAL::discoveryState;
    private static final AsteroidFieldDiscoveryProvider DISCOVERY_DOMAIN = new AsteroidFieldDiscoveryProvider(GLOBAL);

    private final Map<UUID, Map<CelestialObjectId, AsteroidFieldKnowledge>> knowledgeByTeam = new LinkedHashMap<>();

    public static AsteroidFieldKnowledgeStore global() {
        return GLOBAL;
    }

    public static CelestialKnowledgeProvider provider() {
        return PROVIDER;
    }

    public static CelestialDiscoveryDomain discoveryDomain() {
        return DISCOVERY_DOMAIN;
    }

    private Optional<DiscoveryState> discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (!key.isMinorBody()) return Optional.empty();
        MinorCelestialBodyId minorBodyId = key.minorBodyId();
        CelestialObjectId beltId = minorBodyId.parentBodyId();
        AsteroidFieldProfile profile = profile(beltId).orElse(null);
        if (profile == null || !profile.hasNodeIndex(minorBodyId.index())) {
            throw new IllegalStateException("Unknown asteroid: " + key);
        }
        Optional<AsteroidFieldKnowledge> knowledge = get(teamId, beltId);
        if (knowledge.isPresent()) {
            return Optional.of(
                knowledge.get()
                    .entryFor(minorBodyId)
                    .detectionState());
        }
        AsteroidFieldNode node = AsteroidFieldResolver.resolveNode(beltId, profile, minorBodyId.index());
        return Optional.of(AsteroidFieldResolver.initialDetectionState(node));
    }

    static Optional<AsteroidFieldProfile> profile(CelestialObjectId beltId) {
        return GalaxiaCelestialAPI.get(beltId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile());
    }

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
