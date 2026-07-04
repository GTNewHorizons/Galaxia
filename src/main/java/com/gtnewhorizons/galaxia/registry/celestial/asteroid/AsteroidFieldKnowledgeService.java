package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeProvider;

public final class AsteroidFieldKnowledgeService {

    private static final AsteroidFieldKnowledgeStore STORE = new AsteroidFieldKnowledgeStore();
    private static final CelestialKnowledgeProvider PROVIDER = new AsteroidFieldDiscoveryProvider(STORE);

    private AsteroidFieldKnowledgeService() {}

    public static CelestialKnowledgeProvider provider() {
        return PROVIDER;
    }

    public static AsteroidFieldKnowledge knowledge(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return STORE.getOrCreate(teamId, beltId, profile);
    }

    public static Optional<AsteroidFieldKnowledge> get(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId) {
        return STORE.get(teamId, beltId);
    }

    public static List<AsteroidFieldKnowledgeSnapshot> snapshots(@Nonnull UUID teamId) {
        return STORE.snapshots(teamId);
    }

    public static Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> snapshotsByTeam() {
        return STORE.snapshotsByTeam();
    }

    public static void restore(@Nonnull UUID teamId, @Nonnull List<AsteroidFieldKnowledgeSnapshot> snapshots,
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        STORE.restore(teamId, snapshots, profileResolver);
    }

    public static void clear() {
        STORE.clear();
    }
}
