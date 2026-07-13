package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public final class AsteroidClientProjectionService {

    private final Map<CelestialObjectKey, CachedProjections> cache = new LinkedHashMap<>();
    private boolean includeHidden;

    private record CachedProjections(List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots, boolean includeHidden,
        List<AsteroidStarmapProjection> projections, Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId) {

        boolean matches(List<AsteroidFieldKnowledgeSnapshot> currentKnowledge,
            List<CelestialDiscoveryScanSnapshot> currentScans, boolean currentIncludeHidden) {
            return knowledgeSnapshots.equals(currentKnowledge) && scanSnapshots.equals(currentScans)
                && includeHidden == currentIncludeHidden;
        }
    }

    public List<AsteroidStarmapProjection> projectionsFor(@Nullable CelestialObject belt,
        @Nonnull List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots,
        @Nonnull List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (belt == null || belt.properties()
            .asteroidFieldProfile() == null) return List.of();
        return projections(belt, knowledgeSnapshots, scanSnapshots).projections();
    }

    public Optional<AsteroidStarmapProjection> projectionFor(@Nullable CelestialObject body,
        @Nonnull List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots,
        @Nonnull List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (body == null || !body.id()
            .isMinorBody()) return Optional.empty();
        return GalaxiaCelestialAPI.get(
            body.id()
                .minorBodyId()
                .parentBodyId())
            .map(
                belt -> projections(belt, knowledgeSnapshots, scanSnapshots).byBodyId()
                    .get(body.id()));
    }

    public List<CelestialObject> childrenOf(@Nullable CelestialObjectKey parentId,
        @Nonnull List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots,
        @Nonnull List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (parentId == null || !parentId.isRegistered()) return List.of();
        return GalaxiaCelestialAPI.get(parentId.registeredBodyId())
            .filter(
                body -> body.properties()
                    .asteroidFieldProfile() != null)
            .map(
                belt -> projections(belt, knowledgeSnapshots, scanSnapshots).projections()
                    .stream()
                    .map(AsteroidStarmapProjection::body)
                    .toList())
            .orElse(List.of());
    }

    public boolean includeHidden() {
        return includeHidden;
    }

    public void setIncludeHidden(boolean value) {
        includeHidden = value;
        cache.clear();
    }

    public void toggleIncludeHidden() {
        setIncludeHidden(!includeHidden);
    }

    public void clear() {
        includeHidden = false;
        cache.clear();
    }

    private CachedProjections projections(CelestialObject belt, List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        CachedProjections cached = cache.get(belt.id());
        if (cached != null && cached.matches(knowledgeSnapshots, scanSnapshots, includeHidden)) return cached;

        Set<MinorCelestialBodyId> scanTargets = scanTargets(belt.id(), scanSnapshots);
        Set<MinorCelestialBodyId> sensorRevealTargets = sensorRevealTargets(belt, knowledgeSnapshots, scanSnapshots);
        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .forBelt(belt, knowledgeSnapshots, includeHidden, scanTargets, sensorRevealTargets);
        Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId = projections.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    projection -> projection.body()
                        .id(),
                    Function.identity()));
        CachedProjections rebuilt = new CachedProjections(
            knowledgeSnapshots,
            scanSnapshots,
            includeHidden,
            projections,
            byBodyId);
        cache.put(belt.id(), rebuilt);
        return rebuilt;
    }

    private static Set<MinorCelestialBodyId> scanTargets(CelestialObjectKey beltId,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (!beltId.isRegistered()) return Set.of();
        CelestialObjectId registeredBeltId = beltId.registeredBodyId();
        Set<MinorCelestialBodyId> targets = new LinkedHashSet<>();
        for (CelestialDiscoveryScanSnapshot snapshot : scanSnapshots) {
            if (snapshot.capability() != CelestialDiscoveryCapability.PROSPECTING) continue;
            if (snapshot.targetKey() != null && snapshot.targetKey()
                .isMinorBody()
                && snapshot.targetKey()
                    .minorBodyId()
                    .parentBodyId() == registeredBeltId) {
                targets.add(
                    snapshot.targetKey()
                        .minorBodyId());
            }
        }
        return Set.copyOf(targets);
    }

    private static Set<MinorCelestialBodyId> sensorRevealTargets(CelestialObject belt,
        List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots, List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null || !belt.id()
            .isRegistered()) return Set.of();
        CelestialObjectId beltId = belt.id()
            .registeredBodyId();
        Optional<AsteroidFieldKnowledgeSnapshot> snapshot = knowledgeSnapshots.stream()
            .filter(candidate -> candidate.beltId() == beltId)
            .findFirst();
        AsteroidFieldNodeCatalog catalog = snapshot
            .map(value -> AsteroidFieldNodeCatalog.fromSnapshots(beltId, profile, value.nodeSnapshots()))
            .orElseGet(
                () -> AsteroidFieldNodeCatalog.restored(beltId)
                    .orElseGet(() -> AsteroidFieldNodeCatalog.fromGenerated(beltId, profile)));
        Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex = new LinkedHashMap<>();
        snapshot.ifPresent(
            value -> value.entries()
                .forEach(entry -> entriesByIndex.put(entry.index(), entry)));

        Set<MinorCelestialBodyId> targets = new LinkedHashSet<>();
        for (CelestialDiscoveryScanSnapshot scan : scanSnapshots) {
            if (scan.capability() != CelestialDiscoveryCapability.PROSPECTING || !scan.anchorKey()
                .isMinorBody()
                || scan.anchorKey()
                    .minorBodyId()
                    .parentBodyId() != beltId)
                continue;
            Optional<AsteroidFieldNode> anchor = catalog.resolve(
                scan.anchorKey()
                    .minorBodyId());
            if (anchor.isEmpty()) continue;
            for (AsteroidFieldNode candidate : catalog.nodes()) {
                if (isHidden(candidate, entriesByIndex)
                    && distance(profile, anchor.get(), candidate) <= scan.radius()) {
                    targets.add(candidate.id());
                }
            }
        }
        return Set.copyOf(targets);
    }

    private static boolean isHidden(AsteroidFieldNode node,
        Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex) {
        AsteroidFieldKnowledgeSnapshot.Entry entry = entriesByIndex.get(node.index());
        DiscoveryState state = entry == null ? node.initialDetectionState() : entry.detectionState();
        return state == DiscoveryState.HIDDEN;
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
