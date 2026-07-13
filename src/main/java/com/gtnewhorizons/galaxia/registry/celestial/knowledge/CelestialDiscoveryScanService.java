package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/**
 * Advances shared celestial discovery work from aggregated satellite workers.
 */
public final class CelestialDiscoveryScanService {

    private final Map<ScanKey, Progress> progressByScan = new LinkedHashMap<>();
    private final Map<ScanKey, DomainBinding> domainByScan = new LinkedHashMap<>();
    private final Map<ScanKey, CelestialDiscoveryScanScope> completedScopes = new LinkedHashMap<>();
    private final Function<CelestialDiscoveryScanScope, CelestialDiscoveryDomain> domainResolver;

    public CelestialDiscoveryScanService() {
        this(CelestialKnowledgeService::discoveryDomain);
    }

    public CelestialDiscoveryScanService(
        @Nonnull Function<CelestialDiscoveryScanScope, CelestialDiscoveryDomain> domainResolver) {
        this.domainResolver = domainResolver;
    }

    public void clear() {
        progressByScan.clear();
        domainByScan.clear();
        completedScopes.clear();
    }

    public List<CelestialDiscoveryScanSnapshot> snapshots(@Nonnull UUID teamId) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        List<CelestialDiscoveryScanSnapshot> snapshots = new ArrayList<>();
        progressByScan.forEach((key, progress) -> {
            if (!key.teamId()
                .equals(teamId)) return;
            snapshots.add(
                new CelestialDiscoveryScanSnapshot(
                    teamId,
                    progress.scope()
                        .anchorKey(),
                    progress.scope()
                        .radius(),
                    progress.scope()
                        .revision(),
                    key.capability(),
                    CelestialDiscoveryScanSnapshot.Status.ACTIVE,
                    progress.work()
                        .targetKey(),
                    progress.work()
                        .step(),
                    progress.elapsedTicks()));
        });
        completedScopes.entrySet()
            .stream()
            .filter(
                entry -> entry.getKey()
                    .teamId()
                    .equals(teamId))
            .map(
                entry -> CelestialDiscoveryScanSnapshot.complete(
                    teamId,
                    entry.getValue(),
                    entry.getKey()
                        .capability()))
            .forEach(snapshots::add);
        return List.copyOf(snapshots);
    }

    public Map<UUID, List<CelestialDiscoveryScanSnapshot>> snapshotsByTeam() {
        Set<UUID> teamIds = new HashSet<>();
        progressByScan.keySet()
            .forEach(key -> teamIds.add(key.teamId()));
        completedScopes.keySet()
            .forEach(key -> teamIds.add(key.teamId()));
        Map<UUID, List<CelestialDiscoveryScanSnapshot>> snapshots = new LinkedHashMap<>();
        teamIds.forEach(teamId -> snapshots.put(teamId, snapshots(teamId)));
        return Map.copyOf(snapshots);
    }

    public void restore(@Nonnull UUID teamId, @Nonnull List<CelestialDiscoveryScanSnapshot> snapshots) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (snapshots == null) throw new IllegalArgumentException("scan snapshots are required");
        Map<ScanKey, Progress> restoredProgress = new LinkedHashMap<>();
        Map<ScanKey, CelestialDiscoveryScanScope> restoredCompletions = new LinkedHashMap<>();
        Set<ScanKey> restored = new HashSet<>();
        for (CelestialDiscoveryScanSnapshot snapshot : snapshots) {
            if (snapshot == null || !snapshot.teamId()
                .equals(teamId)) {
                throw new IllegalArgumentException("scan snapshot team does not match restore team");
            }
            ScanKey key = new ScanKey(teamId, snapshot.anchorKey(), snapshot.capability());
            if (!restored.add(key)) throw new IllegalArgumentException("duplicate discovery scan key " + key);
            if (snapshot.status() == CelestialDiscoveryScanSnapshot.Status.COMPLETE) {
                restoredCompletions.put(key, snapshot.scope());
            } else {
                restoredProgress.put(
                    key,
                    new Progress(
                        snapshot.scope(),
                        new RestoredWork(snapshot.targetKey(), snapshot.step()),
                        snapshot.elapsedTicks()));
            }
        }
        progressByScan.keySet()
            .removeIf(
                key -> key.teamId()
                    .equals(teamId));
        domainByScan.keySet()
            .removeIf(
                key -> key.teamId()
                    .equals(teamId));
        completedScopes.keySet()
            .removeIf(
                key -> key.teamId()
                    .equals(teamId));
        progressByScan.putAll(restoredProgress);
        completedScopes.putAll(restoredCompletions);
    }

    public List<CelestialDiscoveryWork> tick(@Nonnull List<CelestialDiscoveryWorkerContribution> workerGroups,
        int elapsedTicks) {
        return tick(workerGroups, elapsedTicks, domainResolver);
    }

    public List<CelestialDiscoveryWork> tick(@Nonnull List<CelestialDiscoveryWorkerContribution> workerGroups,
        int elapsedTicks, @Nonnull Function<CelestialDiscoveryScanScope, CelestialDiscoveryDomain> domainResolver) {
        if (workerGroups == null) throw new IllegalArgumentException("worker groups are required");
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed ticks must be non-negative");

        List<CelestialDiscoveryWork> completed = new ArrayList<>();
        for (CelestialDiscoveryWorkerContribution workers : workerGroups) {
            if (workers == null) throw new IllegalArgumentException("worker group cannot be null");
            long effectiveTicks = workers.effectiveTicks(elapsedTicks);
            if (effectiveTicks == 0) continue;

            ScanKey key = new ScanKey(
                workers.teamId(),
                workers.scope()
                    .anchorKey(),
                workers.capability());
            if (workers.scope()
                .equals(completedScopes.get(key))) {
                progressByScan.remove(key);
                domainByScan.remove(key);
                continue;
            }
            completedScopes.remove(key);
            advanceScan(key, workers.scope(), effectiveTicks, completed, domainResolver);
        }
        return List.copyOf(completed);
    }

    private void advanceScan(ScanKey key, CelestialDiscoveryScanScope scope, long effectiveTicks,
        List<CelestialDiscoveryWork> completed,
        Function<CelestialDiscoveryScanScope, CelestialDiscoveryDomain> domainResolver) {
        DomainBinding binding = domainByScan.get(key);
        if (binding == null || !binding.scope()
            .equals(scope)) {
            binding = new DomainBinding(scope, domainResolver.apply(scope));
            domainByScan.put(key, binding);
        }
        CelestialDiscoveryDomain domain = binding.domain();
        long remainingTicks = effectiveTicks;
        while (remainingTicks > 0) {
            CelestialDiscoveryWork work = domain.nextDiscoveryWork(key.teamId(), scope)
                .orElse(null);
            if (work == null) {
                progressByScan.remove(key);
                domainByScan.remove(key);
                completedScopes.put(key, scope);
                return;
            }
            if (work.durationTicks() <= 0) throw new IllegalStateException("discovery work duration must be positive");

            Progress progress = progressByScan.get(key);
            if (progress == null || !progress.scope()
                .equals(scope)
                || !progress.work()
                    .targetKey()
                    .equals(work.targetKey())
                || progress.work()
                    .step() != work.step()) {
                progress = new Progress(scope, work, 0);
            }

            long requiredTicks = work.durationTicks() - progress.elapsedTicks();
            if (remainingTicks < requiredTicks) {
                progressByScan
                    .put(key, new Progress(scope, work, Math.addExact(progress.elapsedTicks(), remainingTicks)));
                return;
            }

            remainingTicks -= requiredTicks;
            domain.completeDiscoveryWork(key.teamId(), scope, work);
            progressByScan.remove(key);
            completed.add(work);
        }
    }

    private record ScanKey(@Nonnull UUID teamId, @Nonnull CelestialObjectKey anchorKey,
        @Nonnull CelestialDiscoveryCapability capability) {}

    private record Progress(@Nonnull CelestialDiscoveryScanScope scope, @Nonnull CelestialDiscoveryWork work,
        long elapsedTicks) {}

    private record DomainBinding(@Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryDomain domain) {}

    private record RestoredWork(@Nonnull CelestialObjectKey targetKey, @Nonnull CelestialDiscoveryStep step)
        implements CelestialDiscoveryWork {}
}
