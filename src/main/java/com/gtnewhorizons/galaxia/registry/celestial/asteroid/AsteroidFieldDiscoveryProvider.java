package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryDomain;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;

final class AsteroidFieldDiscoveryProvider implements CelestialDiscoveryDomain {

    private final AsteroidFieldKnowledgeStore knowledgeStore;

    AsteroidFieldDiscoveryProvider(@Nonnull AsteroidFieldKnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    @Override
    public boolean ownsDiscoveryAnchor(@Nonnull CelestialObjectKey anchorKey) {
        if (!anchorKey.isMinorBody()) return false;
        MinorCelestialBodyId anchorId = anchorKey.minorBodyId();
        return profile(anchorId.parentBodyId()).filter(field -> field.hasNodeIndex(anchorId.index()))
            .isPresent();
    }

    @Override
    public boolean ownsDiscoveryScope(@Nonnull CelestialDiscoveryScanScope scope) {
        if (!scope.anchorKey()
            .isMinorBody()) return false;
        MinorCelestialBodyId anchorId = scope.anchorKey()
            .minorBodyId();
        return profile(anchorId.parentBodyId()).filter(field -> field.hasNodeIndex(anchorId.index()))
            .filter(field -> field.generationVersion() == scope.revision())
            .isPresent();
    }

    @Override
    public OptionalLong discoveryScopeRevision(@Nonnull CelestialObjectKey anchorKey) {
        if (!anchorKey.isMinorBody()) return OptionalLong.empty();
        MinorCelestialBodyId anchorId = anchorKey.minorBodyId();
        return profile(anchorId.parentBodyId()).filter(field -> field.hasNodeIndex(anchorId.index()))
            .map(field -> OptionalLong.of(field.generationVersion()))
            .orElseGet(OptionalLong::empty);
    }

    @Override
    public Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull UUID teamId,
        @Nonnull CelestialDiscoveryScanScope scope) {
        AsteroidFieldProfile profile = requireProfile(scope);
        CelestialObjectId beltId = scope.anchorKey()
            .minorBodyId()
            .parentBodyId();
        AsteroidFieldKnowledge knowledge = knowledgeStore.getOrCreate(teamId, beltId, profile);
        return knowledge
            .nextDiscoveryWork(AsteroidFieldScanContext.from(beltId, profile, scope.anchorKey(), scope.radius()));
    }

    @Override
    public void completeDiscoveryWork(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryWork work) {
        AsteroidFieldProfile profile = requireProfile(scope);
        CelestialObjectId beltId = scope.anchorKey()
            .minorBodyId()
            .parentBodyId();
        AsteroidFieldKnowledge knowledge = knowledgeStore.getOrCreate(teamId, beltId, profile);
        knowledge
            .revealDiscovery(work, AsteroidFieldScanContext.from(beltId, profile, scope.anchorKey(), scope.radius()));
    }

    private static Optional<AsteroidFieldProfile> profile(CelestialObjectId beltId) {
        return GalaxiaCelestialAPI.get(beltId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile());
    }

    private static AsteroidFieldProfile requireProfile(CelestialDiscoveryScanScope scope) {
        if (!scope.anchorKey()
            .isMinorBody()) {
            throw new IllegalArgumentException("asteroid discovery requires a minor-body anchor");
        }
        MinorCelestialBodyId anchorId = scope.anchorKey()
            .minorBodyId();
        AsteroidFieldProfile profile = profile(anchorId.parentBodyId())
            .orElseThrow(() -> new IllegalStateException("Unknown asteroid field for " + scope.anchorKey()));
        if (!profile.hasNodeIndex(anchorId.index()) || profile.generationVersion() != scope.revision()) {
            throw new IllegalStateException("Stale asteroid discovery scope " + scope);
        }
        return profile;
    }
}
