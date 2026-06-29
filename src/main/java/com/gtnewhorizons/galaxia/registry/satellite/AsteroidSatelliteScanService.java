package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitModel;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

public final class AsteroidSatelliteScanService {

    private static final OrbitalMechanics.OrbitalState LOCAL_BELT_REFERENCE = new OrbitalMechanics.OrbitalState(
        1.0,
        0.0,
        0.0,
        0.0);

    private final AsteroidFieldKnowledgeStore knowledgeStore;
    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;
    private final Map<ScanKey, Progress> progressBySatellite = new LinkedHashMap<>();
    private final Set<CompletionKey> completions = new HashSet<>();

    public AsteroidSatelliteScanService(AsteroidFieldKnowledgeStore knowledgeStore,
        Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "knowledgeStore cannot be null");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver cannot be null");
    }

    public void clear() {
        progressBySatellite.clear();
        completions.clear();
    }

    public List<AsteroidSatelliteScanSnapshot> snapshots(UUID teamId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        List<AsteroidSatelliteScanSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<ScanKey, Progress> entry : progressBySatellite.entrySet()) {
            if (!entry.getKey()
                .teamId()
                .equals(teamId)) continue;
            Progress progress = entry.getValue();
            snapshots.add(
                new AsteroidSatelliteScanSnapshot(
                    entry.getKey()
                        .satelliteId(),
                    progress.asteroidId()
                        .parentBeltId(),
                    progress.asteroidId(),
                    progress.pass(),
                    progress.elapsedTicks()));
        }
        return List.copyOf(snapshots);
    }

    public Map<UUID, List<AsteroidSatelliteScanSnapshot>> snapshotsByTeam() {
        Map<UUID, List<AsteroidSatelliteScanSnapshot>> snapshots = new LinkedHashMap<>();
        for (ScanKey key : progressBySatellite.keySet()) {
            snapshots.put(key.teamId(), snapshots(key.teamId()));
        }
        return Map.copyOf(snapshots);
    }

    public List<AsteroidSatelliteScanCompletionSnapshot> completionSnapshots(UUID teamId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
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

    public void restore(UUID teamId, List<AsteroidSatelliteScanSnapshot> snapshots) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(snapshots, "snapshots cannot be null");
        progressBySatellite.keySet()
            .removeIf(
                key -> key.teamId()
                    .equals(teamId));
        for (AsteroidSatelliteScanSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot cannot be null");
            ScanKey key = new ScanKey(teamId, snapshot.satelliteId());
            if (progressBySatellite.containsKey(key)) {
                throw new IllegalStateException(
                    "Duplicate asteroid scan snapshot for satellite " + snapshot.satelliteId());
            }
            progressBySatellite.put(key, new Progress(snapshot.pass(), snapshot.asteroidId(), snapshot.elapsedTicks()));
        }
    }

    public void restoreCompletions(UUID teamId, List<AsteroidSatelliteScanCompletionSnapshot> snapshots) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(snapshots, "snapshots cannot be null");
        completions.removeIf(
            key -> key.teamId()
                .equals(teamId));
        for (AsteroidSatelliteScanCompletionSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot cannot be null");
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

    public List<ScanResult> tick(UUID teamId, List<CelestialAsset> assets, int elapsedTicks) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(assets, "assets cannot be null");
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
        progressBySatellite.keySet()
            .removeIf(
                key -> key.teamId()
                    .equals(teamId) && !activeKeys.contains(key));
        return List.copyOf(results);
    }

    private List<ScanResult> tickSatellite(UUID teamId, Satellite satellite, ScanKey key, int elapsedTicks) {
        if (!satellite.celestialObjectId.isMinorBody()) {
            progressBySatellite.remove(key);
            return List.of();
        }

        MinorCelestialBodyId anchorId = satellite.celestialObjectId.minorBodyId();
        CelestialObjectId beltId = anchorId.parentBeltId();
        Optional<AsteroidFieldProfile> profile = Objects
            .requireNonNull(profileResolver.apply(beltId), "profileResolver cannot return null");
        if (profile.isEmpty()) {
            progressBySatellite.remove(key);
            return List.of();
        }

        AsteroidFieldKnowledge knowledge = knowledgeStore.getOrCreate(teamId, beltId, profile.get());
        Predicate<AsteroidFieldNode> scope = scanScope(beltId, profile.get(), anchorId);
        CompletionKey completionKey = new CompletionKey(
            teamId,
            beltId,
            anchorId,
            profile.get()
                .generationVersion());
        if (completions.contains(completionKey)) {
            progressBySatellite.remove(key);
            return List.of();
        }

        List<ScanResult> results = new ArrayList<>();
        int remainingTicks = elapsedTicks;
        while (remainingTicks > 0) {
            Optional<ScanWork> work = nextWork(knowledge, scope);
            if (work.isEmpty()) {
                progressBySatellite.remove(key);
                completions.add(completionKey);
                break;
            }
            Progress progress = currentProgress(key, work.get());
            int requiredTicks = work.get()
                .pass()
                .durationTicks() - progress.elapsedTicks();
            if (remainingTicks < requiredTicks) {
                progressBySatellite.put(key, progress.advance(remainingTicks));
                break;
            }

            remainingTicks -= requiredTicks;
            completeWork(knowledge, work.get(), scope);
            progressBySatellite.remove(key);
            results.add(
                new ScanResult(
                    beltId,
                    work.get()
                        .asteroidId(),
                    work.get()
                        .pass()));
            if (nextWork(knowledge, scope).isEmpty()) {
                completions.add(completionKey);
                break;
            }
        }
        return results;
    }

    private Progress currentProgress(ScanKey key, ScanWork work) {
        Progress current = progressBySatellite.get(key);
        if (current == null || !current.matches(work)) return Progress.empty(work);
        return current;
    }

    private static Predicate<AsteroidFieldNode> scanScope(CelestialObjectId beltId, AsteroidFieldProfile profile,
        MinorCelestialBodyId anchorId) {
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(beltId, profile, anchorId.index());
        OrbitalMechanics.OrbitalState center = AsteroidFieldOrbitModel
            .resolveWorldState(profile, anchor, LOCAL_BELT_REFERENCE);
        return AsteroidFieldScanScope
            .withinRadius(profile, LOCAL_BELT_REFERENCE, center, profile.satelliteScanRadius());
    }

    private static Optional<ScanWork> nextWork(AsteroidFieldKnowledge knowledge, Predicate<AsteroidFieldNode> scope) {
        Optional<AsteroidFieldNode> detection = knowledge
            .nextDetectionCandidate(scope, AsteroidFieldScanOrder.innerToOuter());
        if (detection.isPresent())
            return detection.map(node -> ScanWork.from(AsteroidSatelliteScanPass.DETECTION, node));

        Optional<AsteroidFieldNode> signature = knowledge
            .nextSignatureCandidate(scope, AsteroidFieldScanOrder.innerToOuter());
        if (signature.isPresent())
            return signature.map(node -> ScanWork.from(AsteroidSatelliteScanPass.SIGNATURE, node));

        return knowledge.nextProfileCandidate(scope, AsteroidFieldScanOrder.innerToOuter())
            .map(node -> ScanWork.from(AsteroidSatelliteScanPass.PROFILE, node));
    }

    private static void completeWork(AsteroidFieldKnowledge knowledge, ScanWork work,
        Predicate<AsteroidFieldNode> scope) {
        if (work.pass() == AsteroidSatelliteScanPass.DETECTION) {
            knowledge.detect(work.asteroidId());
            return;
        }
        knowledge.prospect(work.asteroidId(), scope);
    }

    public record ScanResult(CelestialObjectId beltId, MinorCelestialBodyId asteroidId,
        AsteroidSatelliteScanPass pass) {

        public ScanResult {
            beltId = Objects.requireNonNull(beltId, "beltId cannot be null");
            asteroidId = Objects.requireNonNull(asteroidId, "asteroidId cannot be null");
            pass = Objects.requireNonNull(pass, "pass cannot be null");
        }
    }

    private record ScanKey(UUID teamId, CelestialAsset.ID satelliteId) {

        private ScanKey {
            teamId = Objects.requireNonNull(teamId, "teamId cannot be null");
            satelliteId = Objects.requireNonNull(satelliteId, "satelliteId cannot be null");
        }
    }

    private record CompletionKey(UUID teamId, CelestialObjectId beltId, MinorCelestialBodyId anchorAsteroidId,
        int generationVersion) {

        private CompletionKey {
            teamId = Objects.requireNonNull(teamId, "teamId cannot be null");
            beltId = Objects.requireNonNull(beltId, "beltId cannot be null");
            anchorAsteroidId = Objects.requireNonNull(anchorAsteroidId, "anchorAsteroidId cannot be null");
            if (!anchorAsteroidId.parentBeltId()
                .equals(beltId)) {
                throw new IllegalArgumentException("anchor asteroid parent belt must match completion belt");
            }
            if (generationVersion <= 0) throw new IllegalArgumentException("generationVersion must be positive");
        }
    }

    private record ScanWork(AsteroidSatelliteScanPass pass, MinorCelestialBodyId asteroidId) {

        private ScanWork {
            pass = Objects.requireNonNull(pass, "pass cannot be null");
            asteroidId = Objects.requireNonNull(asteroidId, "asteroidId cannot be null");
        }

        private static ScanWork from(AsteroidSatelliteScanPass pass, AsteroidFieldNode node) {
            return new ScanWork(pass, node.id());
        }
    }

    private record Progress(AsteroidSatelliteScanPass pass, MinorCelestialBodyId asteroidId, int elapsedTicks) {

        private Progress {
            pass = Objects.requireNonNull(pass, "pass cannot be null");
            asteroidId = Objects.requireNonNull(asteroidId, "asteroidId cannot be null");
            if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        }

        private static Progress empty(ScanWork work) {
            return new Progress(work.pass(), work.asteroidId(), 0);
        }

        private boolean matches(ScanWork work) {
            return pass == work.pass() && asteroidId.equals(work.asteroidId());
        }

        private Progress advance(int ticks) {
            if (ticks < 0) throw new IllegalArgumentException("ticks must be non-negative");
            return new Progress(pass, asteroidId, elapsedTicks + ticks);
        }
    }
}
