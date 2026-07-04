package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanWork;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.progress.ProgressJobRunner;

/**
 * Advances prospecting satellites assigned to asteroid minor bodies.
 *
 * A satellite scans around its anchor asteroid, not the whole belt. Work is split
 * into ordered passes: detect hidden bodies first, then reveal ore signatures,
 * then reveal full ore profiles.
 */
public final class AsteroidSatelliteScanService {

    private static final OrbitalMechanics.OrbitalState LOCAL_BELT_REFERENCE = new OrbitalMechanics.OrbitalState(
        1.0,
        0.0,
        0.0,
        0.0);

    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;
    private final ProgressJobRunner<ScanKey, AsteroidFieldScanWork, ScanResult> progressRunner = new ProgressJobRunner<>();
    // Completion is keyed by profile generation so a future belt reshuffle can
    // rescan, while an unchanged field stays permanently idle after a full pass.
    private final Set<CompletionKey> completions = new HashSet<>();

    public AsteroidSatelliteScanService(
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        this.profileResolver = profileResolver;
    }

    public void clear() {
        progressRunner.clear();
        completions.clear();
    }

    public List<AsteroidSatelliteScanSnapshot> snapshots(@Nonnull UUID teamId) {
        List<AsteroidSatelliteScanSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<ScanKey, ProgressJobRunner.Progress<AsteroidFieldScanWork>> entry : progressRunner
            .progressByKey()
            .entrySet()) {
            if (!entry.getKey()
                .teamId()
                .equals(teamId)) continue;
            ProgressJobRunner.Progress<AsteroidFieldScanWork> progress = entry.getValue();
            AsteroidFieldScanWork work = progress.work();
            snapshots.add(
                new AsteroidSatelliteScanSnapshot(
                    entry.getKey()
                        .satelliteId(),
                    work.asteroidId()
                        .parentBodyId(),
                    work.asteroidId(),
                    work.pass(),
                    progress.elapsedTicks()));
        }
        return List.copyOf(snapshots);
    }

    public Map<UUID, List<AsteroidSatelliteScanSnapshot>> snapshotsByTeam() {
        Map<UUID, List<AsteroidSatelliteScanSnapshot>> snapshots = new LinkedHashMap<>();
        for (ScanKey key : progressRunner.progressByKey()
            .keySet()) {
            snapshots.put(key.teamId(), snapshots(key.teamId()));
        }
        return Map.copyOf(snapshots);
    }

    public List<AsteroidSatelliteScanCompletionSnapshot> completionSnapshots(@Nonnull UUID teamId) {
        return completions.stream()
            .filter(
                key -> key.teamId()
                    .equals(teamId))
            .map(
                key -> new AsteroidSatelliteScanCompletionSnapshot(
                    key.beltId(),
                    key.anchorAsteroidId(),
                    key.generationVersion()))
            .toList();
    }

    public Map<UUID, List<AsteroidSatelliteScanCompletionSnapshot>> completionSnapshotsByTeam() {
        Map<UUID, List<AsteroidSatelliteScanCompletionSnapshot>> snapshots = new LinkedHashMap<>();
        for (CompletionKey key : completions) {
            snapshots.put(key.teamId(), completionSnapshots(key.teamId()));
        }
        return Map.copyOf(snapshots);
    }

    public void restore(@Nonnull UUID teamId, @Nonnull List<AsteroidSatelliteScanSnapshot> snapshots) {
        progressRunner.removeIf(
            key -> key.teamId()
                .equals(teamId));
        for (AsteroidSatelliteScanSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshot cannot be null");
            }
            ScanKey key = new ScanKey(teamId, snapshot.satelliteId());
            if (progressRunner.progress(key)
                .isPresent()) {
                throw new IllegalStateException(
                    "Duplicate asteroid scan snapshot for satellite " + snapshot.satelliteId());
            }
            progressRunner.restore(
                key,
                new AsteroidFieldScanWork(snapshot.pass(), snapshot.asteroidId()),
                snapshot.elapsedTicks());
        }
    }

    public void restoreCompletions(@Nonnull UUID teamId,
        @Nonnull List<AsteroidSatelliteScanCompletionSnapshot> snapshots) {
        completions.removeIf(
            key -> key.teamId()
                .equals(teamId));
        for (AsteroidSatelliteScanCompletionSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("completion snapshot cannot be null");
            }
            CompletionKey key = new CompletionKey(
                teamId,
                snapshot.beltId(),
                snapshot.anchorAsteroidId(),
                snapshot.generationVersion());
            if (!completions.add(key)) {
                throw new IllegalStateException(
                    "Duplicate asteroid scan completion for anchor " + snapshot.anchorAsteroidId());
            }
        }
    }

    public List<ScanResult> tick(@Nonnull UUID teamId, @Nonnull List<CelestialAsset> assets, int elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        if (elapsedTicks == 0) return List.of();

        List<ScanResult> results = new ArrayList<>();
        Set<ScanKey> activeKeys = new java.util.HashSet<>();
        for (CelestialAsset asset : assets) {
            if (!(asset instanceof Satellite satellite) || !satellite.isOperational()
                || satellite.satelliteKind() != SatelliteKind.PROSPECTING) {
                continue;
            }
            ScanKey key = new ScanKey(teamId, satellite.assetId);
            activeKeys.add(key);
            results.addAll(tickSatellite(teamId, satellite, key, elapsedTicks));
        }
        progressRunner.removeIf(
            key -> key.teamId()
                .equals(teamId) && !activeKeys.contains(key));
        return List.copyOf(results);
    }

    private List<ScanResult> tickSatellite(UUID teamId, Satellite satellite, ScanKey key, int elapsedTicks) {
        if (!satellite.celestialObjectId.isMinorBody()) {
            progressRunner.remove(key);
            return List.of();
        }

        MinorCelestialBodyId anchorId = satellite.celestialObjectId.minorBodyId();
        CelestialObjectId beltId = anchorId.parentBodyId();
        Optional<AsteroidFieldProfile> profile = profileResolver.apply(beltId);
        if (profile == null) {
            throw new IllegalStateException("profileResolver cannot return null");
        }
        if (profile.isEmpty()) {
            progressRunner.remove(key);
            return List.of();
        }

        AsteroidFieldKnowledge knowledge = CelestialKnowledgeService
            .asteroidFieldKnowledge(teamId, beltId, profile.get());
        Predicate<AsteroidFieldNode> scope = scanScope(beltId, profile.get(), anchorId);
        CompletionKey completionKey = new CompletionKey(
            teamId,
            beltId,
            anchorId,
            profile.get()
                .generationVersion());
        if (completions.contains(completionKey)) {
            progressRunner.remove(key);
            return List.of();
        }

        ProgressJobRunner.TickResult<ScanResult> result = progressRunner.tick(
            key,
            elapsedTicks,
            () -> knowledge.nextScanWork(scope, AsteroidFieldScanOrder.innerToOuter()),
            work -> {
                knowledge.completeScanWork(work, scope);
                return new ScanResult(beltId, work.asteroidId(), work.pass());
            });
        if (result.idle()) {
            // A full pass that finds no more work disables active scanning for
            // this anchor until the field generation changes.
            completions.add(completionKey);
        }
        return result.results();
    }

    private static Predicate<AsteroidFieldNode> scanScope(CelestialObjectId beltId, AsteroidFieldProfile profile,
        MinorCelestialBodyId anchorId) {
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(beltId, profile, anchorId.index());
        OrbitalMechanics.OrbitalState center = OrbitalMechanics
            .resolveAsteroidFieldWorldState(profile, anchor, LOCAL_BELT_REFERENCE);
        return AsteroidFieldScanScope
            .withinRadius(profile, LOCAL_BELT_REFERENCE, center, profile.satelliteScanRadius());
    }

    public record ScanResult(@Nonnull CelestialObjectId beltId, @Nonnull MinorCelestialBodyId asteroidId,
        @Nonnull AsteroidFieldScanPass pass) {}

    private record ScanKey(@Nonnull UUID teamId, @Nonnull CelestialAsset.ID satelliteId) {}

    private record CompletionKey(@Nonnull UUID teamId, @Nonnull CelestialObjectId beltId,
        @Nonnull MinorCelestialBodyId anchorAsteroidId, int generationVersion) {

        private CompletionKey {
            if (!anchorAsteroidId.parentBodyId()
                .equals(beltId)) {
                throw new IllegalArgumentException("anchor asteroid parent body must match completion belt");
            }
            if (generationVersion <= 0) throw new IllegalArgumentException("generationVersion must be positive");
        }
    }

}
