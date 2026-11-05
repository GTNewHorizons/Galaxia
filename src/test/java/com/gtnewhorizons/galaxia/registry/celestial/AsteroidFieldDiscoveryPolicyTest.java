package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidFieldDiscoveryPolicyTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000790");

    private AsteroidFieldDiscoveryPolicy policy;
    private AsteroidFieldProfile profile;
    private List<AsteroidFieldNode> nodes;

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void setUp() {
        CelestialKnowledgeService.clearFacts();
        CelestialKnowledgeService.resetDiscoveryDomainsForTesting();
        policy = new AsteroidFieldDiscoveryPolicy();
        CelestialKnowledgeService.registerDiscoveryDomain(policy);
        profile = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);
    }

    @Test
    void detectionRunsBeforeProspectingInSameScope() {
        AsteroidFieldNode anchor = firstHidden(AsteroidSizeClass.MEDIUM);
        CelestialDiscoveryScanScope scope = wideScope(anchor);

        CelestialDiscoveryWork first = policy.nextDiscoveryWork(TEAM, scope)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.DETECTION, first.step());
        policy.completeDiscoveryWork(TEAM, scope, first);

        // Keep completing detection until none remain in scope; then PROFILE starts.
        CelestialDiscoveryWork next;
        while ((next = policy.nextDiscoveryWork(TEAM, scope)
            .orElse(null)) != null && next.step() == CelestialDiscoveryStep.DETECTION) {
            policy.completeDiscoveryWork(TEAM, scope, next);
        }
        CelestialDiscoveryWork prospecting = policy.nextDiscoveryWork(TEAM, scope)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.PROFILE, prospecting.step());
    }

    @Test
    void completionMutatesOneSharedFact() {
        AsteroidFieldNode hidden = firstHidden(AsteroidSizeClass.MEDIUM);
        CelestialObjectKey key = CelestialObjectKey.minorBody(hidden.id());
        CelestialDiscoveryScanScope scope = wideScope(hidden);
        assertEquals(DiscoveryState.HIDDEN, CelestialKnowledgeService.discoveryState(TEAM, key));

        CelestialDiscoveryWork work = new CelestialDiscoveryWork(key, CelestialDiscoveryStep.DETECTION);
        policy.completeDiscoveryWork(TEAM, scope, work);

        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeService.discoveryState(TEAM, key));
    }

    @Test
    void prospectingAdvancesResourceFromUnknownDirectlyToProfile() {
        AsteroidFieldNode hidden = firstHidden(AsteroidSizeClass.SMALL);
        CelestialObjectKey key = CelestialObjectKey.minorBody(hidden.id());
        CelestialDiscoveryScanScope scope = nodeOnlyScope(hidden);

        policy.completeDiscoveryWork(TEAM, scope, new CelestialDiscoveryWork(key, CelestialDiscoveryStep.DETECTION));
        assertEquals(CelestialResourceKnowledgeState.UNKNOWN, CelestialKnowledgeService.resourceKnowledge(TEAM, key));

        policy.completeDiscoveryWork(TEAM, scope, new CelestialDiscoveryWork(key, CelestialDiscoveryStep.PROFILE));
        assertEquals(CelestialResourceKnowledgeState.PROFILE, CelestialKnowledgeService.resourceKnowledge(TEAM, key));
    }

    @Test
    void cannotProspectHiddenTarget() {
        AsteroidFieldNode hidden = firstHidden(AsteroidSizeClass.MEDIUM);
        CelestialDiscoveryScanScope scope = nodeOnlyScope(hidden);
        CelestialDiscoveryWork work = new CelestialDiscoveryWork(
            CelestialObjectKey.minorBody(hidden.id()),
            CelestialDiscoveryStep.PROFILE);

        assertThrows(IllegalStateException.class, () -> policy.completeDiscoveryWork(TEAM, scope, work));
    }

    @Test
    void scopeRadiusLimitsCandidates() {
        AsteroidFieldNode near = firstHidden(AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode far = nodes.stream()
            .filter(
                node -> node.initialDetectionState() == DiscoveryState.HIDDEN && !node.id()
                    .equals(near.id()))
            .filter(node -> distance(near, node) > 0.05)
            .findFirst()
            .orElseThrow();

        CelestialDiscoveryScanScope tight = new CelestialDiscoveryScanScope(
            CelestialObjectKey.minorBody(near.id()),
            0.0,
            scopeRevision(near));
        CelestialDiscoveryWork work = policy.nextDiscoveryWork(TEAM, tight)
            .orElseThrow();
        assertEquals(CelestialObjectKey.minorBody(near.id()), work.targetKey());
        assertTrue(
            policy.nextDiscoveryWork(TEAM, tight)
                .stream()
                .noneMatch(
                    candidate -> candidate.targetKey()
                        .equals(CelestialObjectKey.minorBody(far.id()))));
    }

    // Product contract: priority remains size then orbital depth while fresh team facts change pending work.
    @Test
    void repeatedSelectionUsesLiveKnowledgeAndPreservesDiscoveryPriority() {
        AsteroidFieldNode earlier = nodes.stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
            .min(Comparator.comparingDouble(AsteroidFieldNode::orbitalDepth01))
            .orElseThrow();
        AsteroidFieldNode later = nodes.stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
            .max(Comparator.comparingDouble(AsteroidFieldNode::orbitalDepth01))
            .orElseThrow();
        AsteroidFieldNode small = firstHidden(AsteroidSizeClass.SMALL);
        for (AsteroidFieldNode node : nodes) {
            CelestialKnowledgeService.putFacts(
                TEAM,
                CelestialObjectKey.minorBody(node.id()),
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
        }
        for (AsteroidFieldNode node : List.of(earlier, later, small)) {
            CelestialKnowledgeService
                .putFacts(TEAM, CelestialObjectKey.minorBody(node.id()), CelestialKnowledgeFacts.hidden());
        }
        CelestialDiscoveryScanScope scope = wideScope(small);

        for (AsteroidFieldNode expected : List.of(earlier, later, small)) {
            CelestialDiscoveryWork work = policy.nextDiscoveryWork(TEAM, scope)
                .orElseThrow();
            assertEquals(CelestialObjectKey.minorBody(expected.id()), work.targetKey());
            assertEquals(CelestialDiscoveryStep.DETECTION, work.step());
            policy.completeDiscoveryWork(TEAM, scope, work);
        }
        assertEquals(
            CelestialDiscoveryStep.PROFILE,
            policy.nextDiscoveryWork(TEAM, scope)
                .orElseThrow()
                .step());
    }

    // Product contract: selection respects the requested scope even after another scope was queried.
    @Test
    void repeatedSelectionSeparatesScopesAndRejectsStaleRevision() {
        AsteroidFieldNode anchor = firstHidden(AsteroidSizeClass.SMALL);
        CelestialDiscoveryScanScope wide = wideScope(anchor);
        policy.nextDiscoveryWork(TEAM, wide)
            .orElseThrow();
        CelestialDiscoveryScanScope tight = nodeOnlyScope(anchor);

        assertEquals(
            CelestialObjectKey.minorBody(anchor.id()),
            policy.nextDiscoveryWork(TEAM, tight)
                .orElseThrow()
                .targetKey());
        CelestialDiscoveryScanScope stale = new CelestialDiscoveryScanScope(
            tight.anchorKey(),
            tight.radius(),
            tight.revision() + 1);
        assertThrows(IllegalStateException.class, () -> policy.nextDiscoveryWork(TEAM, stale));
        assertEquals(
            CelestialObjectKey.minorBody(anchor.id()),
            policy.nextDiscoveryWork(TEAM, tight)
                .orElseThrow()
                .targetKey());
    }

    // Product contract: shared geometry never shares knowledge, and profiling still waits for detection.
    @Test
    void reusedScopeReadsEachTeamsFactsAndRetainsCompletionGuard() {
        AsteroidFieldNode anchor = firstHidden(AsteroidSizeClass.MEDIUM);
        CelestialDiscoveryScanScope scope = nodeOnlyScope(anchor);
        CelestialDiscoveryWork detection = policy.nextDiscoveryWork(TEAM, scope)
            .orElseThrow();
        UUID otherTeam = new UUID(80L, 81L);
        policy.completeDiscoveryWork(TEAM, scope, detection);

        assertEquals(
            CelestialDiscoveryStep.PROFILE,
            policy.nextDiscoveryWork(TEAM, scope)
                .orElseThrow()
                .step());
        assertEquals(
            CelestialDiscoveryStep.DETECTION,
            policy.nextDiscoveryWork(otherTeam, scope)
                .orElseThrow()
                .step());
        assertThrows(
            IllegalStateException.class,
            () -> policy.completeDiscoveryWork(
                otherTeam,
                scope,
                new CelestialDiscoveryWork(detection.targetKey(), CelestialDiscoveryStep.PROFILE)));
        CelestialDiscoveryScanScope wide = wideScope(anchor);
        assertEquals(
            CelestialDiscoveryStep.DETECTION,
            policy.nextDiscoveryWork(TEAM, wide)
                .orElseThrow()
                .step());
        assertThrows(
            IllegalStateException.class,
            () -> policy.completeDiscoveryWork(
                TEAM,
                wide,
                new CelestialDiscoveryWork(detection.targetKey(), CelestialDiscoveryStep.PROFILE)));
    }

    private CelestialDiscoveryScanScope wideScope(AsteroidFieldNode anchor) {
        return new CelestialDiscoveryScanScope(
            CelestialObjectKey.minorBody(anchor.id()),
            1_000_000.0,
            scopeRevision(anchor));
    }

    private CelestialDiscoveryScanScope nodeOnlyScope(AsteroidFieldNode anchor) {
        return new CelestialDiscoveryScanScope(CelestialObjectKey.minorBody(anchor.id()), 0.0, scopeRevision(anchor));
    }

    private long scopeRevision(AsteroidFieldNode anchor) {
        return policy.discoveryScopeRevision(CelestialObjectKey.minorBody(anchor.id()))
            .orElseThrow();
    }

    private AsteroidFieldNode firstHidden(AsteroidSizeClass size) {
        return nodes.stream()
            .filter(node -> node.sizeClass() == size)
            .filter(node -> node.initialDetectionState() == DiscoveryState.HIDDEN)
            .findFirst()
            .orElseThrow();
    }

    private double distance(AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver
            .resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver
            .resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
