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
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialDiscoveryScanContractTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000778");
    private static final CelestialObjectKey PLANET = CelestialObjectKey.registered(CelestialObjectId.MARS);

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void resetProviders() {
        CelestialKnowledgeService.resetProvidersForTesting();
    }

    @Test
    void registeredBodyProviderOwnsGenericDiscoveryScope() {
        PlanetKnowledgeProvider knowledge = new PlanetKnowledgeProvider();
        PlanetDiscoveryDomain domain = new PlanetDiscoveryDomain(knowledge);
        CelestialKnowledgeService.registerProvider(knowledge);
        CelestialKnowledgeService.registerDiscoveryDomain(domain);
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

    private static final class PlanetKnowledgeProvider implements CelestialKnowledgeProvider {

        private DiscoveryState state = DiscoveryState.HIDDEN;

        @Override
        public Optional<DiscoveryState> discoveryState(UUID teamId, CelestialObjectKey key) {
            return PLANET.equals(key) ? Optional.of(state) : Optional.empty();
        }

    }

    private static final class PlanetDiscoveryDomain implements CelestialDiscoveryDomain {

        private final PlanetKnowledgeProvider knowledge;
        private CelestialDiscoveryScanScope receivedScope;

        private PlanetDiscoveryDomain(PlanetKnowledgeProvider knowledge) {
            this.knowledge = knowledge;
        }

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
            return knowledge.state == DiscoveryState.HIDDEN
                ? Optional.of(new PlanetDiscoveryWork(PLANET, CelestialDiscoveryStep.DETECTION))
                : Optional.empty();
        }

        @Override
        public void completeDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope, CelestialDiscoveryWork work) {
            receivedScope = scope;
            knowledge.state = DiscoveryState.DISCOVERED;
        }
    }

    private record PlanetDiscoveryWork(CelestialObjectKey targetKey, CelestialDiscoveryStep step)
        implements CelestialDiscoveryWork {}
}
