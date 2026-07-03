package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
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

    private CelestialKnowledgeService() {}

    public static AsteroidFieldKnowledgeStore asteroidFields() {
        return ASTEROID_FIELDS;
    }

    public static boolean isDiscovered(@Nonnull UUID teamId, @Nonnull MinorCelestialBodyId minorBodyId) {
        AsteroidFieldProfile profile = profile(minorBodyId.parentBeltId()).orElse(null);
        if (profile == null || !profile.hasNodeIndex(minorBodyId.index())) return false;

        Optional<AsteroidFieldKnowledge> knowledge = ASTEROID_FIELDS.get(teamId, minorBodyId.parentBeltId());
        if (knowledge.isPresent()) {
            return knowledge.get()
                .entryFor(minorBodyId)
                .detectionState() == DiscoveryState.DISCOVERED;
        }

        AsteroidFieldNode node = AsteroidFieldResolver
            .resolveNode(minorBodyId.parentBeltId(), profile, minorBodyId.index());
        return AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.DISCOVERED;
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
