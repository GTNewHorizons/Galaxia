package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialDiscoveryScanServiceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000779");
    private static final CelestialObjectKey PLANET = CelestialObjectKey.registered(CelestialObjectId.MARS);
    private static final CelestialDiscoveryScanScope SCOPE = new CelestialDiscoveryScanScope(PLANET, 0.25, 7L);

    private PlanetDiscoveryDomain provider;
    private CelestialDiscoveryScanService service;

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void setUp() {
        CelestialKnowledgeService.resetProvidersForTesting();
        provider = new PlanetDiscoveryDomain();
        CelestialKnowledgeService.registerDiscoveryDomain(provider);
        service = new CelestialDiscoveryScanService();
    }

    @AfterEach
    void clearAssets() {
        CelestialAssetStore.clear();
    }

    @Test
    void genericKnowledgeLifecycleAdvancesSatelliteWorkersWithoutDomainOwnedTick() {
        CelestialAssetStore.registerAsset(
            TEAM,
            CelestialAsset.create(
                PLANET,
                CelestialAsset.Kind.SATELLITE,
                Buildable.Status.OPERATIONAL,
                SatelliteKind.PROSPECTING));

        new CelestialDiscoveryRuntime(
            () -> com.gtnewhorizons.galaxia.registry.satellite.SatelliteDiscoveryWorkerSource
                .prospectingWorkers(CelestialKnowledgeService::discoveryScopeRevision),
            service).tick(CelestialDiscoveryStep.DETECTION.durationTicks());

        assertEquals(DiscoveryState.DISCOVERED, provider.state);
    }

    @Test
    void workersAtOneAnchorContributeToOneSharedDiscoveryJob() {
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 600);

        assertEquals(DiscoveryState.HIDDEN, provider.state);

        List<CelestialDiscoveryWork> completed = service.tick(List.of(worker(SCOPE, 2, 1.0)), 300);

        assertEquals(1, completed.size());
        assertEquals(DiscoveryState.DISCOVERED, provider.state);
        assertEquals(1, provider.completionCount);
    }

    @Test
    void activeScopeKeepsItsResolvedDomainAcrossTicks() {
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 300);
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 300);

        assertEquals(1, provider.scopeOwnershipChecks);
        assertEquals(DiscoveryState.HIDDEN, provider.state);
    }

    @Test
    void remainingTickBudgetAdvancesFollowingDiscoveryWork() {
        SequencedPlanetKnowledgeProvider sequenced = new SequencedPlanetKnowledgeProvider();
        CelestialKnowledgeService.resetProvidersForTesting();
        CelestialKnowledgeService.registerDiscoveryDomain(sequenced);

        List<CelestialDiscoveryWork> completed = service.tick(List.of(worker(SCOPE, 1, 1.0)), 1000);

        assertEquals(2, completed.size());
        assertEquals(2, sequenced.completedFacts);
    }

    @Test
    void exhaustedScopeStaysIdleUntilItsRevisionChanges() {
        RevisionPlanetKnowledgeProvider revisionProvider = new RevisionPlanetKnowledgeProvider();
        CelestialKnowledgeService.resetProvidersForTesting();
        CelestialKnowledgeService.registerDiscoveryDomain(revisionProvider);
        CelestialDiscoveryWorkerContribution currentRevision = worker(SCOPE, 1, 1.0);

        service.tick(List.of(currentRevision), 100);
        revisionProvider.workAvailable = true;
        service.tick(List.of(currentRevision), 100);

        assertEquals(DiscoveryState.HIDDEN, revisionProvider.state);

        CelestialDiscoveryScanScope changedScope = new CelestialDiscoveryScanScope(PLANET, SCOPE.radius(), 8L);
        service.tick(List.of(worker(changedScope, 1, 1.0)), 100);

        assertEquals(DiscoveryState.DISCOVERED, revisionProvider.state);
    }

    @Test
    void changedScopeReplacesCompletionForTheSameDiscoveryScan() {
        CelestialKnowledgeService.resetProvidersForTesting();
        CelestialKnowledgeService.registerDiscoveryDomain(new RevisionPlanetKnowledgeProvider());
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 100);
        CelestialDiscoveryScanScope changedScope = new CelestialDiscoveryScanScope(PLANET, 0.5, 8L);

        service.tick(List.of(worker(changedScope, 1, 1.0)), 100);

        assertEquals(
            List.of(
                CelestialDiscoveryScanSnapshot.complete(TEAM, changedScope, CelestialDiscoveryCapability.PROSPECTING)),
            service.snapshots(TEAM));
    }

    @Test
    void activeDiscoveryProgressSurvivesRestore() {
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 600);
        List<CelestialDiscoveryScanSnapshot> snapshots = service.snapshots(TEAM);

        service = new CelestialDiscoveryScanService();
        service.restore(TEAM, snapshots);
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 600);

        assertEquals(DiscoveryState.DISCOVERED, provider.state);
    }

    @Test
    void completedDiscoveryScopeRemainsIdleAfterRestore() {
        RevisionPlanetKnowledgeProvider revisionProvider = new RevisionPlanetKnowledgeProvider();
        CelestialKnowledgeService.resetProvidersForTesting();
        CelestialKnowledgeService.registerDiscoveryDomain(revisionProvider);
        CelestialDiscoveryWorkerContribution workers = worker(SCOPE, 1, 1.0);
        service.tick(List.of(workers), 100);
        service.tick(List.of(workers), 100);
        List<CelestialDiscoveryScanSnapshot> completedScope = service.snapshots(TEAM);

        service = new CelestialDiscoveryScanService();
        service.restore(TEAM, completedScope);
        revisionProvider.workAvailable = true;
        service.tick(List.of(workers), 100);

        assertEquals(DiscoveryState.HIDDEN, revisionProvider.state);
    }

    @Test
    void restoreRejectsDuplicateDiscoveryLifecycleKeys() {
        CelestialDiscoveryScanSnapshot completed = CelestialDiscoveryScanSnapshot
            .complete(TEAM, SCOPE, CelestialDiscoveryCapability.PROSPECTING);

        assertThrows(IllegalArgumentException.class, () -> service.restore(TEAM, List.of(completed, completed)));
    }

    @Test
    void failedRestoreLeavesCurrentDiscoveryStateUnchanged() {
        service.tick(List.of(worker(SCOPE, 1, 1.0)), 600);
        List<CelestialDiscoveryScanSnapshot> currentState = service.snapshots(TEAM);
        CelestialDiscoveryScanSnapshot completed = CelestialDiscoveryScanSnapshot
            .complete(TEAM, SCOPE, CelestialDiscoveryCapability.PROSPECTING);

        assertThrows(IllegalArgumentException.class, () -> service.restore(TEAM, List.of(completed, completed)));

        assertEquals(currentState, service.snapshots(TEAM));
    }

    @Test
    void ambiguousDiscoveryDomainsFailBeforeSelectingRevisionOrWork() {
        AmbiguousPlanetDomain first = new AmbiguousPlanetDomain(7L);
        AmbiguousPlanetDomain second = new AmbiguousPlanetDomain(8L);
        CelestialKnowledgeService.resetProvidersForTesting();
        CelestialKnowledgeService.registerDiscoveryDomain(first);
        CelestialKnowledgeService.registerDiscoveryDomain(second);
        CelestialAssetStore.registerAsset(
            TEAM,
            CelestialAsset.create(
                PLANET,
                CelestialAsset.Kind.SATELLITE,
                Buildable.Status.OPERATIONAL,
                SatelliteKind.PROSPECTING));

        assertThrows(
            IllegalStateException.class,
            () -> new CelestialDiscoveryRuntime(
                () -> com.gtnewhorizons.galaxia.registry.satellite.SatelliteDiscoveryWorkerSource
                    .prospectingWorkers(CelestialKnowledgeService::discoveryScopeRevision),
                service).tick(1));
        assertEquals(0, first.revisionSelections);
        assertEquals(0, second.revisionSelections);
        assertEquals(0, first.workSelections);
        assertEquals(0, second.workSelections);
    }

    private static CelestialDiscoveryWorkerContribution worker(CelestialDiscoveryScanScope scope, int count,
        double effectPerWorker) {
        return new CelestialDiscoveryWorkerContribution(
            TEAM,
            scope,
            CelestialDiscoveryCapability.PROSPECTING,
            count,
            effectPerWorker);
    }

    private static final class PlanetDiscoveryDomain implements CelestialDiscoveryDomain {

        private DiscoveryState state = DiscoveryState.HIDDEN;
        private int completionCount;
        private int scopeOwnershipChecks;

        @Override
        public boolean ownsDiscoveryAnchor(CelestialObjectKey anchorKey) {
            return PLANET.equals(anchorKey);
        }

        @Override
        public boolean ownsDiscoveryScope(CelestialDiscoveryScanScope scope) {
            scopeOwnershipChecks++;
            return PLANET.equals(scope.anchorKey()) && SCOPE.revision() == scope.revision();
        }

        @Override
        public OptionalLong discoveryScopeRevision(CelestialObjectKey anchorKey) {
            return PLANET.equals(anchorKey) ? OptionalLong.of(SCOPE.revision()) : OptionalLong.empty();
        }

        @Override
        public Optional<CelestialDiscoveryWork> nextDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope) {
            return state == DiscoveryState.HIDDEN ? Optional.of(new PlanetDiscoveryWork(PLANET)) : Optional.empty();
        }

        @Override
        public void completeDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope, CelestialDiscoveryWork work) {
            completionCount++;
            state = DiscoveryState.DISCOVERED;
        }
    }

    private record PlanetDiscoveryWork(CelestialObjectKey targetKey) implements CelestialDiscoveryWork {

        @Override
        public CelestialDiscoveryStep step() {
            return CelestialDiscoveryStep.DETECTION;
        }

        @Override
        public int durationTicks() {
            return 1200;
        }
    }

    private static final class SequencedPlanetKnowledgeProvider implements CelestialDiscoveryDomain {

        private int completedFacts;

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
            return PLANET.equals(anchorKey) ? OptionalLong.of(SCOPE.revision()) : OptionalLong.empty();
        }

        @Override
        public Optional<CelestialDiscoveryWork> nextDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope) {
            return Optional.of(new SequencedWork(PLANET, completedFacts));
        }

        @Override
        public void completeDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope, CelestialDiscoveryWork work) {
            completedFacts++;
        }
    }

    private record SequencedWork(CelestialObjectKey targetKey, int sequence) implements CelestialDiscoveryWork {

        @Override
        public CelestialDiscoveryStep step() {
            return CelestialDiscoveryStep.DETECTION;
        }

        @Override
        public int durationTicks() {
            return 400;
        }
    }

    private static final class RevisionPlanetKnowledgeProvider implements CelestialDiscoveryDomain {

        private DiscoveryState state = DiscoveryState.HIDDEN;
        private boolean workAvailable;

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
            return PLANET.equals(anchorKey) ? OptionalLong.of(SCOPE.revision()) : OptionalLong.empty();
        }

        @Override
        public Optional<CelestialDiscoveryWork> nextDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope) {
            return workAvailable ? Optional.of(new RevisionWork(PLANET)) : Optional.empty();
        }

        @Override
        public void completeDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope, CelestialDiscoveryWork work) {
            state = DiscoveryState.DISCOVERED;
            workAvailable = false;
        }
    }

    private static final class AmbiguousPlanetDomain implements CelestialDiscoveryDomain {

        private final long revision;
        private int revisionSelections;
        private int workSelections;

        private AmbiguousPlanetDomain(long revision) {
            this.revision = revision;
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
            revisionSelections++;
            return PLANET.equals(anchorKey) ? OptionalLong.of(revision) : OptionalLong.empty();
        }

        @Override
        public Optional<CelestialDiscoveryWork> nextDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope) {
            workSelections++;
            return Optional.empty();
        }

        @Override
        public void completeDiscoveryWork(UUID teamId, CelestialDiscoveryScanScope scope,
            CelestialDiscoveryWork work) {}
    }

    private record RevisionWork(CelestialObjectKey targetKey) implements CelestialDiscoveryWork {

        @Override
        public CelestialDiscoveryStep step() {
            return CelestialDiscoveryStep.DETECTION;
        }

        @Override
        public int durationTicks() {
            return 100;
        }
    }
}
