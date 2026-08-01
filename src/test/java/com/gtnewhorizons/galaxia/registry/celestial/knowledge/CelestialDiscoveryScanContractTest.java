package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialDiscoveryScanContractTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000778");
    private static final CelestialObjectKey PLANET = CelestialObjectKey.registered(CelestialObjectId.MARS);

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void reset() {
        CelestialKnowledgeService.clearFacts();
        CelestialKnowledgeService.resetDiscoveryDomainsForTesting();
    }

    @Test
    void planetDomainWritesFactsThroughSharedService() {
        PlanetDiscoveryDomain domain = new PlanetDiscoveryDomain();
        CelestialKnowledgeService.registerDiscoveryDomain(domain);
        CelestialKnowledgeService.putFacts(TEAM, PLANET, CelestialKnowledgeFacts.hidden());
        CelestialDiscoveryScanScope scope = new CelestialDiscoveryScanScope(PLANET, 0.25, 7L);

        assertEquals(DiscoveryState.HIDDEN, CelestialKnowledgeService.discoveryState(TEAM, PLANET));

        CelestialDiscoveryWork work = CelestialKnowledgeService.nextDiscoveryWork(TEAM, scope)
            .orElseThrow();
        assertEquals(PLANET, work.targetKey());
        assertEquals(CelestialDiscoveryStep.DETECTION, work.step());

        CelestialKnowledgeService.completeDiscoveryWork(TEAM, scope, work);

        assertSame(scope, domain.receivedScope);
        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeService.discoveryState(TEAM, PLANET));
    }

    private static final class PlanetDiscoveryDomain implements CelestialDiscoveryDomain {

        private CelestialDiscoveryScanScope receivedScope;

        @Override
        public boolean ownsDiscoveryAnchor(CelestialObjectKey anchorKey) {
            return PLANET.equals(anchorKey);
        }

        @Override
        public boolean ownsDiscoveryScope(CelestialDiscoveryScanScope scope) {
            return PLANET.equals(scope.anchorKey());
        }

        @Override
        public OptionalLong discoveryScopeRevision(CelestialObjectKey anchorKey) {
            return PLANET.equals(anchorKey) ? OptionalLong.of(7L) : OptionalLong.empty();
        }

        @Override
        public Optional<CelestialDiscoveryWork> nextDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope) {
            receivedScope = scope;
            return CelestialKnowledgeService.discoveryState(teamId, PLANET) == DiscoveryState.HIDDEN
                ? Optional.of(new CelestialDiscoveryWork(PLANET, CelestialDiscoveryStep.DETECTION))
                : Optional.empty();
        }

        @Override
        public void completeDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope, CelestialDiscoveryWork work) {
            receivedScope = scope;
            CelestialKnowledgeService.putFacts(teamId, PLANET, CelestialKnowledgeFacts.discoveredUnknown());
        }
    }
}
