package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class AsteroidFieldDiscoveryProvider implements CelestialDiscoveryProvider {

    private final AsteroidFieldKnowledgeStore knowledgeStore;
    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;

    AsteroidFieldDiscoveryProvider(@Nonnull AsteroidFieldKnowledgeStore knowledgeStore,
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        if (knowledgeStore == null) throw new IllegalArgumentException("knowledge store is required");
        if (profileResolver == null) throw new IllegalArgumentException("profile resolver is required");
        this.knowledgeStore = knowledgeStore;
        this.profileResolver = profileResolver;
    }

    @Override
    public Optional<DiscoveryState> discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        if (!key.isMinorBody()) return Optional.empty();

        MinorCelestialBodyId minorBodyId = key.minorBodyId();
        CelestialObjectId beltId = minorBodyId.parentBeltId();
        AsteroidFieldProfile profile = profileResolver.apply(beltId)
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
}
