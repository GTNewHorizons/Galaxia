package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeProvider;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidFieldDiscoveryProvider implements CelestialKnowledgeProvider {

    private final AsteroidFieldKnowledgeStore knowledgeStore;

    AsteroidFieldDiscoveryProvider(@Nonnull AsteroidFieldKnowledgeStore knowledgeStore) {
        if (knowledgeStore == null) throw new IllegalArgumentException("knowledge store is required");
        this.knowledgeStore = knowledgeStore;
    }

    @Override
    public Optional<DiscoveryState> discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        if (!key.isMinorBody()) return Optional.empty();

        MinorCelestialBodyId minorBodyId = key.minorBodyId();
        CelestialObjectId beltId = minorBodyId.parentBodyId();
        AsteroidFieldProfile profile = GalaxiaCelestialAPI.get(beltId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile())
            .orElse(null);
        if (profile == null || !profile.hasNodeIndex(minorBodyId.index())) {
            throw new IllegalStateException("Unknown asteroid: " + key);
        }

        Optional<AsteroidFieldKnowledge> knowledge = knowledgeStore.get(teamId, beltId);
        if (knowledge.isPresent()) {
            return Optional.of(
                knowledge.get()
                    .entryFor(minorBodyId)
                    .detectionState());
        }

        AsteroidFieldNode node = AsteroidFieldResolver.resolveNode(beltId, profile, minorBodyId.index());
        return Optional.of(AsteroidFieldResolver.initialDetectionState(node));
    }

    @Override
    public void clear() {
        knowledgeStore.clear();
    }
}
