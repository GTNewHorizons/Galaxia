package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public interface AsteroidFieldKnowledgeAccess {

    boolean hasDetectionWork(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile);

    Optional<AsteroidFieldNode> nextDetectionCandidate(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope);

    Optional<AsteroidFieldNode> nextSignatureCandidate(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope);

    Optional<AsteroidFieldNode> nextProfileCandidate(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull Predicate<AsteroidFieldNode> scope);

    void detect(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        @Nonnull MinorCelestialBodyId asteroidId);

    void prospect(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        @Nonnull MinorCelestialBodyId asteroidId, @Nonnull Predicate<AsteroidFieldNode> scope);

    Optional<AsteroidFieldNode> detectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile);

    Optional<AsteroidFieldNode> prospectNext(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile);
}
