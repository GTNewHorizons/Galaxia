package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryDomain;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

/**
 * Stateless asteroid-field discovery policy.
 * <p>
 * TLDR: selects which asteroid scan work to run next from the deterministic content
 * catalog plus the team's effective facts, and writes completed facts back through
 * {@link CelestialKnowledgeService}. It holds no team state of its own; the shared
 * knowledge owner is the only mutable store.
 */
final class AsteroidFieldDiscoveryPolicy implements CelestialDiscoveryDomain {

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
            .filter(field -> AsteroidFieldResolver.layoutRevision(anchorId.parentBodyId(), field) == scope.revision())
            .isPresent();
    }

    @Override
    public OptionalLong discoveryScopeRevision(@Nonnull CelestialObjectKey anchorKey) {
        if (!anchorKey.isMinorBody()) return OptionalLong.empty();
        MinorCelestialBodyId anchorId = anchorKey.minorBodyId();
        return profile(anchorId.parentBodyId()).filter(field -> field.hasNodeIndex(anchorId.index()))
            .map(field -> OptionalLong.of(AsteroidFieldResolver.layoutRevision(anchorId.parentBodyId(), field)))
            .orElseGet(OptionalLong::empty);
    }

    @Override
    public Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull UUID teamId,
        @Nonnull CelestialDiscoveryScanScope scope) {
        AsteroidFieldProfile profile = requireProfile(scope);
        CelestialObjectId beltId = scope.anchorKey()
            .minorBodyId()
            .parentBodyId();
        List<AsteroidFieldNode> nodes = catalogNodes(beltId, profile);
        Predicate<AsteroidFieldNode> inScope = scopePredicate(beltId, profile, scope.anchorKey(), scope.radius());
        Comparator<AsteroidFieldNode> order = discoveryOrder();

        Optional<AsteroidFieldNode> detection = firstScoped(
            nodes,
            inScope,
            order,
            node -> discoveryState(teamId, node) == DiscoveryState.HIDDEN);
        if (detection.isPresent()) return detection.map(node -> work(node, CelestialDiscoveryStep.DETECTION));

        // Detection must finish across the whole scope before prospecting starts so
        // the UI can reveal existence before ore details.
        if (hasDetectionWork(teamId, nodes, inScope)) return Optional.empty();

        return firstScoped(
            nodes,
            inScope,
            order,
            node -> discoveryState(teamId, node) == DiscoveryState.DISCOVERED
                && resourceKnowledge(teamId, node) == CelestialResourceKnowledgeState.UNKNOWN)
                    .map(node -> work(node, CelestialDiscoveryStep.PROFILE));
    }

    @Override
    public void completeDiscoveryWork(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryWork work) {
        AsteroidFieldProfile profile = requireProfile(scope);
        CelestialObjectKey targetKey = work.targetKey();
        if (!targetKey.isMinorBody()) {
            throw new IllegalArgumentException("Asteroid discovery work must target a minor body");
        }
        MinorCelestialBodyId targetId = targetKey.minorBodyId();
        CelestialObjectId beltId = scope.anchorKey()
            .minorBodyId()
            .parentBodyId();
        if (targetId.parentBodyId() != beltId || !profile.hasNodeIndex(targetId.index())) {
            throw new IllegalArgumentException("Asteroid discovery work target is outside the scan belt: " + targetKey);
        }
        AsteroidFieldNode node = AsteroidFieldResolver.placedNode(beltId, profile, targetId.index());
        CelestialKnowledgeFacts current = CelestialKnowledgeService.facts(teamId, targetKey);

        if (work.step() == CelestialDiscoveryStep.DETECTION) {
            if (current.discoveryState() == DiscoveryState.DISCOVERED) return;
            // Detection only reveals the body. Ore details require a PROFILE scan.
            CelestialKnowledgeService.putFacts(
                teamId,
                targetKey,
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN));
            return;
        }

        Predicate<AsteroidFieldNode> inScope = scopePredicate(beltId, profile, scope.anchorKey(), scope.radius());
        if (!inScope.test(node)) {
            throw new IllegalArgumentException("Asteroid node is outside prospecting scope: " + targetKey);
        }
        if (current.discoveryState() == DiscoveryState.HIDDEN) {
            throw new IllegalStateException("Cannot prospect hidden asteroid node: " + targetKey);
        }
        if (hasDetectionWork(teamId, catalogNodes(beltId, profile), inScope)) {
            throw new IllegalStateException("Asteroid detection must finish before prospecting can start");
        }
        // A completed PROFILE scan is the only way to learn ore details.
        CelestialKnowledgeService.putFacts(
            teamId,
            targetKey,
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
    }

    private DiscoveryState discoveryState(UUID teamId, AsteroidFieldNode node) {
        return CelestialKnowledgeService.discoveryState(teamId, CelestialObjectKey.minorBody(node.id()));
    }

    private CelestialResourceKnowledgeState resourceKnowledge(UUID teamId, AsteroidFieldNode node) {
        return CelestialKnowledgeService.resourceKnowledge(teamId, CelestialObjectKey.minorBody(node.id()));
    }

    private boolean hasDetectionWork(UUID teamId, List<AsteroidFieldNode> nodes, Predicate<AsteroidFieldNode> inScope) {
        return nodes.stream()
            .filter(inScope)
            .anyMatch(node -> discoveryState(teamId, node) == DiscoveryState.HIDDEN);
    }

    private static CelestialDiscoveryWork work(AsteroidFieldNode node, CelestialDiscoveryStep step) {
        return new CelestialDiscoveryWork(CelestialObjectKey.minorBody(node.id()), step);
    }

    private static Optional<AsteroidFieldNode> firstScoped(List<AsteroidFieldNode> nodes,
        Predicate<AsteroidFieldNode> inScope, Comparator<AsteroidFieldNode> order,
        Predicate<AsteroidFieldNode> candidate) {
        return nodes.stream()
            .filter(inScope)
            .sorted(order)
            .filter(candidate)
            .findFirst();
    }

    private static List<AsteroidFieldNode> catalogNodes(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        return AsteroidFieldResolver.resolveAll(beltId, profile);
    }

    // Inner-to-outer, importance-weighted ordering: a satellite parked on one
    // asteroid reveals nearby belt depth consistently instead of jumping by slot id.
    private static Comparator<AsteroidFieldNode> discoveryOrder() {
        return Comparator.comparingInt(AsteroidFieldDiscoveryPolicy::importance)
            .reversed()
            .thenComparingDouble(AsteroidFieldNode::orbitalDepth01)
            .thenComparingInt(AsteroidFieldNode::index);
    }

    private static int importance(AsteroidFieldNode node) {
        return switch (node.sizeClass()) {
            case LARGE -> 30;
            case MEDIUM -> 20;
            case SMALL -> 10;
        };
    }

    private static Predicate<AsteroidFieldNode> scopePredicate(CelestialObjectId beltId, AsteroidFieldProfile profile,
        CelestialObjectKey anchorKey, double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }
        MinorCelestialBodyId anchorId = anchorKey.minorBodyId();
        AsteroidFieldNode anchor = AsteroidFieldResolver.placedNode(beltId, profile, anchorId.index());
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(1.0, 0.0, 0.0, 0.0);
        OrbitalMechanics.OrbitalState center = AsteroidFieldOrbitResolver.resolveWorldState(profile, anchor, beltState);
        double radiusSquared = radius * radius;
        return node -> {
            OrbitalMechanics.OrbitalState asteroidState = AsteroidFieldOrbitResolver
                .resolveWorldState(profile, node, beltState);
            double dx = asteroidState.x() - center.x();
            double dy = asteroidState.y() - center.y();
            return dx * dx + dy * dy <= radiusSquared;
        };
    }

    private static Optional<AsteroidFieldProfile> profile(CelestialObjectId beltId) {
        return CelestialRegistry.get(beltId)
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
        if (!profile.hasNodeIndex(anchorId.index())
            || AsteroidFieldResolver.layoutRevision(anchorId.parentBodyId(), profile) != scope.revision()) {
            throw new IllegalStateException("Stale asteroid discovery scope " + scope);
        }
        return profile;
    }
}
