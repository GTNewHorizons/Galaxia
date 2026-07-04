package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public interface AsteroidFieldKnowledge extends CelestialDiscoveryKnowledge<AsteroidFieldScanContext> {

    List<AsteroidFieldNode> nodes();

    Entry entryFor(@Nonnull MinorCelestialBodyId id);

    Optional<AsteroidFieldNode> nextDetectionCandidate();

    Optional<AsteroidFieldNode> nextDetectionCandidate(@Nonnull Predicate<AsteroidFieldNode> scope);

    Optional<AsteroidFieldNode> nextDetectionCandidate(@Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order);

    boolean hasDetectionWork();

    boolean hasDetectionWork(@Nonnull Predicate<AsteroidFieldNode> scope);

    boolean canProspect();

    boolean canProspect(@Nonnull Predicate<AsteroidFieldNode> scope);

    Optional<AsteroidFieldNode> nextProspectingCandidate();

    Optional<AsteroidFieldNode> nextProspectingCandidate(@Nonnull Predicate<AsteroidFieldNode> scope);

    Optional<AsteroidFieldNode> nextProspectingCandidate(@Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order);

    Optional<AsteroidFieldNode> nextSignatureCandidate(@Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order);

    Optional<AsteroidFieldNode> nextProfileCandidate(@Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order);

    Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull Predicate<AsteroidFieldNode> scope,
        @Nonnull Comparator<AsteroidFieldNode> order);

    Entry detect(@Nonnull MinorCelestialBodyId id);

    Entry prospect(@Nonnull MinorCelestialBodyId id);

    Entry prospect(@Nonnull MinorCelestialBodyId id, @Nonnull Predicate<AsteroidFieldNode> scope);

    Entry revealDiscovery(@Nonnull CelestialDiscoveryWork work, @Nonnull Predicate<AsteroidFieldNode> scope);

    AsteroidFieldKnowledgeSnapshot snapshot(@Nonnull CelestialObjectId beltId);

    @Override
    default Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull AsteroidFieldScanContext context) {
        if (context == null) throw new IllegalArgumentException("scan context is required");
        return nextDiscoveryWork(context.scope(), context.order());
    }

    @Override
    default void revealDiscovery(@Nonnull CelestialDiscoveryWork work, @Nonnull AsteroidFieldScanContext context) {
        if (context == null) throw new IllegalArgumentException("scan context is required");
        revealDiscovery(work, context.scope());
    }

    record Entry(@Nonnull DiscoveryState detectionState, @Nonnull AsteroidOreKnowledgeState oreKnowledgeState) {}
}
