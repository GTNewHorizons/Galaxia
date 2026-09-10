package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Asteroid-field discovery policy with shared immutable geometric candidates.
 * <p>
 * TLDR: selects which asteroid scan work to run next from the deterministic content
 * catalog plus the team's effective facts, and writes completed facts back through
 * {@link CelestialKnowledgeService}. Cached geometry contains no team facts.
 */
final class AsteroidFieldDiscoveryPolicy implements CelestialDiscoveryDomain {

    private static final int LARGE_DISCOVERY_IMPORTANCE = 30;
    private static final int MEDIUM_DISCOVERY_IMPORTANCE = 20;
    private static final int SMALL_DISCOVERY_IMPORTANCE = 10;
    private static final double MINIMUM_SCAN_RADIUS = 0.0;
    private final Map<CelestialObjectId, CandidateField> candidatesByField = new HashMap<>();

    private record CandidateField(AsteroidFieldProfile profile,
        Map<CelestialDiscoveryScanScope, List<AsteroidFieldNode>> scopes) {}

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
        List<AsteroidFieldNode> nodes = candidates(scope, profile);
        Optional<AsteroidFieldNode> detection = nodes.stream()
            .filter(node -> discoveryState(teamId, node) == DiscoveryState.HIDDEN)
            .findFirst();
        if (detection.isPresent()) return detection.map(node -> work(node, CelestialDiscoveryStep.DETECTION));

        // Detection must finish across the whole scope before prospecting starts so
        // the UI can reveal existence before ore details.
        return nodes.stream()
            .filter(
                node -> discoveryState(teamId, node) == DiscoveryState.DISCOVERED
                    && resourceKnowledge(teamId, node) == CelestialResourceKnowledgeState.UNKNOWN)
            .findFirst()
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
        if (hasDetectionWork(teamId, candidates(scope, profile))) {
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

    private boolean hasDetectionWork(UUID teamId, List<AsteroidFieldNode> nodes) {
        return nodes.stream()
            .anyMatch(node -> discoveryState(teamId, node) == DiscoveryState.HIDDEN);
    }

    private static CelestialDiscoveryWork work(AsteroidFieldNode node, CelestialDiscoveryStep step) {
        return new CelestialDiscoveryWork(CelestialObjectKey.minorBody(node.id()), step);
    }

    private List<AsteroidFieldNode> candidates(CelestialDiscoveryScanScope scope, AsteroidFieldProfile profile) {
        CelestialObjectId beltId = scope.anchorKey()
            .minorBodyId()
            .parentBodyId();
        CandidateField field = candidatesByField.get(beltId);
        if (field == null || !field.profile()
            .equals(profile)) {
            field = new CandidateField(profile, new HashMap<>());
            candidatesByField.put(beltId, field);
        }
        return field.scopes()
            .computeIfAbsent(
                scope,
                ignored -> AsteroidFieldResolver.resolveAll(beltId, profile)
                    .stream()
                    .filter(scopePredicate(beltId, profile, scope.anchorKey(), scope.radius()))
                    .sorted(discoveryOrder())
                    .toList());
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
            case LARGE -> LARGE_DISCOVERY_IMPORTANCE;
            case MEDIUM -> MEDIUM_DISCOVERY_IMPORTANCE;
            case SMALL -> SMALL_DISCOVERY_IMPORTANCE;
        };
    }

    private static Predicate<AsteroidFieldNode> scopePredicate(CelestialObjectId beltId, AsteroidFieldProfile profile,
        CelestialObjectKey anchorKey, double radius) {
        if (!Double.isFinite(radius) || radius < MINIMUM_SCAN_RADIUS) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }
        MinorCelestialBodyId anchorId = anchorKey.minorBodyId();
        AsteroidFieldNode anchor = AsteroidFieldResolver.placedNode(beltId, profile, anchorId.index());
        return node -> AsteroidFieldOrbitResolver.separation(profile, anchor, node) <= radius;
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
