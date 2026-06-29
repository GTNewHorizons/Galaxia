package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
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
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public final class AsteroidSatelliteScanService {

    private static final Predicate<AsteroidFieldNode> WHOLE_FIELD = node -> true;

    private final AsteroidFieldKnowledgeStore knowledgeStore;
    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;
    private final Map<ScanKey, Progress> progressBySatellite = new LinkedHashMap<>();

    public AsteroidSatelliteScanService(AsteroidFieldKnowledgeStore knowledgeStore,
        Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "knowledgeStore cannot be null");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver cannot be null");
    }

    public void clear() {
        progressBySatellite.clear();
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
        List<ScanResult> results = new ArrayList<>();
        int remainingTicks = elapsedTicks;
        while (remainingTicks > 0) {
            Optional<ScanWork> work = nextWork(knowledge);
            if (work.isEmpty()) {
                progressBySatellite.remove(key);
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
            completeWork(knowledge, work.get());
            progressBySatellite.remove(key);
            results.add(
                new ScanResult(
                    beltId,
                    work.get()
                        .asteroidId(),
                    work.get()
                        .pass()));
        }
        return results;
    }

    private Progress currentProgress(ScanKey key, ScanWork work) {
        Progress current = progressBySatellite.get(key);
        if (current == null || !current.matches(work)) return Progress.empty(work);
        return current;
    }

    private static Optional<ScanWork> nextWork(AsteroidFieldKnowledge knowledge) {
        Optional<AsteroidFieldNode> detection = knowledge
            .nextDetectionCandidate(WHOLE_FIELD, AsteroidFieldScanOrder.innerToOuter());
        if (detection.isPresent())
            return detection.map(node -> ScanWork.from(AsteroidSatelliteScanPass.DETECTION, node));

        Optional<AsteroidFieldNode> signature = knowledge
            .nextSignatureCandidate(WHOLE_FIELD, AsteroidFieldScanOrder.innerToOuter());
        if (signature.isPresent())
            return signature.map(node -> ScanWork.from(AsteroidSatelliteScanPass.SIGNATURE, node));

        return knowledge.nextProfileCandidate(WHOLE_FIELD, AsteroidFieldScanOrder.innerToOuter())
            .map(node -> ScanWork.from(AsteroidSatelliteScanPass.PROFILE, node));
    }

    private static void completeWork(AsteroidFieldKnowledge knowledge, ScanWork work) {
        if (work.pass() == AsteroidSatelliteScanPass.DETECTION) {
            knowledge.detect(work.asteroidId());
            return;
        }
        knowledge.prospect(work.asteroidId(), WHOLE_FIELD);
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
