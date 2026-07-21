package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
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

        // Keep completing detection until none remain in scope; then SIGNATURE starts.
        CelestialDiscoveryWork next;
        while ((next = policy.nextDiscoveryWork(TEAM, scope)
            .orElse(null)) != null && next.step() == CelestialDiscoveryStep.DETECTION) {
            policy.completeDiscoveryWork(TEAM, scope, next);
        }
        CelestialDiscoveryWork prospecting = policy.nextDiscoveryWork(TEAM, scope)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.SIGNATURE, prospecting.step());
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
    void prospectingAdvancesResourceThroughSignatureThenProfile() {
        AsteroidFieldNode hidden = firstHidden(AsteroidSizeClass.SMALL);
        CelestialObjectKey key = CelestialObjectKey.minorBody(hidden.id());
        CelestialDiscoveryScanScope scope = nodeOnlyScope(hidden);

        // Detect the single in-scope node, then prospect twice.
        policy.completeDiscoveryWork(TEAM, scope, new CelestialDiscoveryWork(key, CelestialDiscoveryStep.DETECTION));
        CelestialKnowledgeService.putFacts(
            TEAM,
            key,
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN));

        policy.completeDiscoveryWork(TEAM, scope, new CelestialDiscoveryWork(key, CelestialDiscoveryStep.SIGNATURE));
        assertEquals(CelestialResourceKnowledgeState.SIGNATURE, CelestialKnowledgeService.resourceKnowledge(TEAM, key));

        policy.completeDiscoveryWork(TEAM, scope, new CelestialDiscoveryWork(key, CelestialDiscoveryStep.PROFILE));
        assertEquals(CelestialResourceKnowledgeState.PROFILE, CelestialKnowledgeService.resourceKnowledge(TEAM, key));
    }

    @Test
    void cannotProspectHiddenTarget() {
        AsteroidFieldNode hidden = firstHidden(AsteroidSizeClass.MEDIUM);
        CelestialDiscoveryScanScope scope = nodeOnlyScope(hidden);
        CelestialDiscoveryWork work = new CelestialDiscoveryWork(
            CelestialObjectKey.minorBody(hidden.id()),
            CelestialDiscoveryStep.SIGNATURE);

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
            profile.generationVersion());
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

    private CelestialDiscoveryScanScope wideScope(AsteroidFieldNode anchor) {
        return new CelestialDiscoveryScanScope(
            CelestialObjectKey.minorBody(anchor.id()),
            1_000_000.0,
            profile.generationVersion());
    }

    private CelestialDiscoveryScanScope nodeOnlyScope(AsteroidFieldNode anchor) {
        return new CelestialDiscoveryScanScope(
            CelestialObjectKey.minorBody(anchor.id()),
            0.0,
            profile.generationVersion());
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
