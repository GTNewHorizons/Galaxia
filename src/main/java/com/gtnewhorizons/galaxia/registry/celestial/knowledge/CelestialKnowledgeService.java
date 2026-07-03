package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/**
 * Server-side owner for team knowledge about celestial objects.
 *
 * Asteroid fields currently provide the only mutable knowledge entries, but
 * callers go through this service so additional discoverable object types can
 * share the same lifecycle, sync, and persistence entry point.
 */
public final class CelestialKnowledgeService {

    private static final AsteroidFieldKnowledgeStore ASTEROID_FIELDS = new AsteroidFieldKnowledgeStore();
    private static final List<CelestialDiscoveryProvider> DISCOVERY_PROVIDERS = List.of(
        new RegisteredCelestialDiscoveryProvider(),
        new AsteroidFieldDiscoveryProvider(ASTEROID_FIELDS, CelestialKnowledgeService::profile));

    private CelestialKnowledgeService() {}

    public static DiscoveryState discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        return DISCOVERY_PROVIDERS.stream()
            .map(provider -> provider.discoveryState(teamId, key))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No discovery provider for celestial object: " + key));
    }

    public static boolean isDiscovered(@Nonnull UUID teamId, @Nonnull MinorCelestialBodyId minorBodyId) {
        return discoveryState(teamId, CelestialObjectKey.minorBody(minorBodyId)) == DiscoveryState.DISCOVERED;
    }

    public static boolean hasAsteroidDetectionWork(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return ASTEROID_FIELDS.hasDetectionWork(teamId, beltId, profile);
    }

    public static Optional<AsteroidFieldNode> nextAsteroidDetectionCandidate(@Nonnull UUID teamId,
        @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        @Nonnull Predicate<AsteroidFieldNode> scope) {
        return ASTEROID_FIELDS.nextDetectionCandidate(teamId, beltId, profile, scope);
    }

    public static Optional<AsteroidFieldNode> nextAsteroidSignatureCandidate(@Nonnull UUID teamId,
        @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        @Nonnull Predicate<AsteroidFieldNode> scope) {
        return ASTEROID_FIELDS.nextSignatureCandidate(teamId, beltId, profile, scope);
    }

    public static Optional<AsteroidFieldNode> nextAsteroidProfileCandidate(@Nonnull UUID teamId,
        @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        @Nonnull Predicate<AsteroidFieldNode> scope) {
        return ASTEROID_FIELDS.nextProfileCandidate(teamId, beltId, profile, scope);
    }

    public static void detectAsteroid(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull MinorCelestialBodyId asteroidId) {
        ASTEROID_FIELDS.detect(teamId, beltId, profile, asteroidId);
    }

    public static void prospectAsteroid(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull MinorCelestialBodyId asteroidId,
        @Nonnull Predicate<AsteroidFieldNode> scope) {
        ASTEROID_FIELDS.prospect(teamId, beltId, profile, asteroidId, scope);
    }

    public static Optional<AsteroidFieldNode> detectNextAsteroid(@Nonnull UUID teamId,
        @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile) {
        return ASTEROID_FIELDS.detectNext(teamId, beltId, profile);
    }

    public static Optional<AsteroidFieldNode> prospectNextAsteroid(@Nonnull UUID teamId,
        @Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile) {
        return ASTEROID_FIELDS.prospectNext(teamId, beltId, profile);
    }

    public static List<AsteroidFieldKnowledgeSnapshot> asteroidFieldSnapshots(@Nonnull UUID teamId) {
        return ASTEROID_FIELDS.snapshots(teamId);
    }

    public static Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> asteroidFieldSnapshotsByTeam() {
        return ASTEROID_FIELDS.snapshotsByTeam();
    }

    public static void restoreAsteroidFields(@Nonnull UUID teamId,
        @Nonnull List<AsteroidFieldKnowledgeSnapshot> snapshots,
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        ASTEROID_FIELDS.restore(teamId, snapshots, profileResolver);
    }

    public static void clear() {
        ASTEROID_FIELDS.clear();
    }

    private static Optional<AsteroidFieldProfile> profile(@Nonnull CelestialObjectId beltId) {
        return CelestialRegistry.get(beltId)
            .map(CelestialObject::properties)
            .map(properties -> properties.asteroidFieldProfile());
    }
}
