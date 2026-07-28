package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
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

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

/**
 * Client presentation adapter for asteroid starmap ghosts and decoration.
 * Supplies temporary scan/sensor visibility to DiscoveryView and decorates
 * canonical Registry children — never owns a second child list.
 */
public final class AsteroidClientProjectionService {

    private final Map<CelestialObjectKey, CachedProjections> cache = new LinkedHashMap<>();
    private boolean includeHidden;

    private record CachedProjections(List<CelestialObject> canonicalSiblings, int knowledgeRevision,
        int discoveryRevision, List<CelestialDiscoveryScanSnapshot> scanSnapshots, boolean includeHidden,
        List<AsteroidStarmapProjection> projections, Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId) {

        boolean matches(List<CelestialObject> currentSiblings, int currentKnowledgeRevision,
            int currentDiscoveryRevision, List<CelestialDiscoveryScanSnapshot> currentScans,
            boolean currentIncludeHidden) {
            return canonicalSiblings == currentSiblings && knowledgeRevision == currentKnowledgeRevision
                && discoveryRevision == currentDiscoveryRevision
                && scanSnapshots.equals(currentScans)
                && includeHidden == currentIncludeHidden;
        }
    }

    /**
     * Discovery view that keeps synced {@code discoveryState} facts intact while
     * treating active scan targets and in-radius sensor ghosts as temporarily visible.
     */
    public CelestialDiscoveryView discoveryView(@Nullable CelestialObjectKey parentKey,
        @Nonnull List<CelestialDiscoveryScanSnapshot> scanSnapshots, @Nonnull CelestialDiscoveryView baseView) {
        Set<CelestialObjectKey> temporaryVisible = temporaryVisibleKeys(parentKey, scanSnapshots);
        return new CelestialDiscoveryView() {

            @Override
            public Optional<DiscoveryState> discoveryState(@Nonnull CelestialObjectKey key) {
                return baseView.discoveryState(key);
            }

            @Override
            public boolean isVisible(@Nonnull CelestialObjectKey key, @Nonnull DiscoveryState initialState) {
                return temporaryVisible.contains(key) || baseView.isVisible(key, initialState);
            }
        };
    }

    public Optional<AsteroidStarmapProjection> projectionFor(@Nullable CelestialObject body,
        @Nonnull List<CelestialObject> canonicalSiblings, @Nonnull List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (body == null || !body.key()
            .isMinorBody()) return Optional.empty();
        CelestialObjectKey parentKey = body.parentKey();
        if (parentKey == null) return Optional.empty();
        return CelestialRegistry.get(parentKey.registeredBodyId())
            .filter(
                belt -> belt.properties()
                    .asteroidFieldProfile() != null)
            .map(
                belt -> projections(belt, canonicalSiblings, scanSnapshots).byBodyId()
                    .get(body.key()));
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

    private CachedProjections projections(CelestialObject belt, List<CelestialObject> canonicalSiblings,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        int knowledgeRevision = CelestialKnowledgeClientState.revision();
        int discoveryRevision = CelestialDiscoveryClientState.revision();
        CachedProjections cached = cache.get(belt.key());
        if (cached != null
            && cached.matches(canonicalSiblings, knowledgeRevision, discoveryRevision, scanSnapshots, includeHidden))
            return cached;

        Set<MinorCelestialBodyId> scanTargets = scanTargets(belt.key(), scanSnapshots);
        Set<MinorCelestialBodyId> sensorRevealTargets = sensorRevealTargets(belt, scanSnapshots);
        List<AsteroidStarmapProjection> projections = decorate(
            belt,
            canonicalSiblings,
            includeHidden,
            scanTargets,
            sensorRevealTargets);
        Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId = projections.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    projection -> projection.body()
                        .key(),
                    Function.identity()));
        CachedProjections rebuilt = new CachedProjections(
            canonicalSiblings,
            knowledgeRevision,
            discoveryRevision,
            scanSnapshots,
            includeHidden,
            projections,
            byBodyId);
        cache.put(belt.key(), rebuilt);
        return rebuilt;
    }

    /**
     * Decorates canonical asteroid bodies already returned by Registry children. Does not enumerate the field catalog
     * or decide child-list membership.
     */
    static List<AsteroidStarmapProjection> decorate(@Nonnull CelestialObject belt,
        @Nonnull List<CelestialObject> canonicalBodies, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets, @Nonnull Set<MinorCelestialBodyId> sensorRevealTargets) {

        if (!belt.key()
            .isRegistered()) {
            throw new IllegalArgumentException("Asteroid starmap projection requires a registered belt body");
        }
        CelestialObjectId beltId = belt.key()
            .registeredBodyId();
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) {
            throw new IllegalArgumentException("Asteroid starmap projection requires an asteroid field profile");
        }

        List<AsteroidStarmapProjection> projections = new ArrayList<>();
        for (CelestialObject body : canonicalBodies) {
            if (body == null || !body.key()
                .isMinorBody()) continue;
            MinorCelestialBodyId minorId = body.key()
                .minorBodyId();
            if (minorId.parentBodyId() != beltId) continue;
            AsteroidFieldResolver.findNode(beltId, profile, minorId.index())
                .ifPresent(
                    node -> projections.add(toProjection(body, node, includeHidden, scanTargets, sensorRevealTargets)));
        }
        return List.copyOf(projections);
    }

    private static AsteroidStarmapProjection toProjection(CelestialObject body, AsteroidFieldNode node,
        boolean includeHidden, Set<MinorCelestialBodyId> scanTargets, Set<MinorCelestialBodyId> sensorRevealTargets) {

        CelestialKnowledgeFacts facts = CelestialKnowledgeClientState.facts(CelestialObjectKey.minorBody(node.id()))
            .orElseGet(
                () -> CelestialKnowledgeFacts
                    .of(node.initialDetectionState(), AsteroidFieldResolver.initialOreKnowledge(node)));
        DiscoveryState detectionState = facts.discoveryState();
        CelestialResourceKnowledgeState oreKnowledgeState = facts.resourceKnowledgeState();
        boolean scanInProgress = detectionState == DiscoveryState.HIDDEN && scanTargets.contains(node.id());
        boolean sensorRevealed = detectionState == DiscoveryState.HIDDEN && !scanInProgress
            && sensorRevealTargets.contains(node.id());

        Optional<String> visibleOreProfileId = oreKnowledgeState == CelestialResourceKnowledgeState.UNKNOWN
            ? Optional.empty()
            : Optional.of(
                node.oreProfile()
                    .id());
        List<String> visibleGtOreVeinIds = oreKnowledgeState == CelestialResourceKnowledgeState.PROFILE
            ? node.oreProfile()
                .gtOreVeinIds()
            : List.of();

        return new AsteroidStarmapProjection(
            body,
            node.id(),
            node.kind(),
            node.sizeClass(),
            detectionState,
            oreKnowledgeState,
            visibleOreProfileId,
            visibleGtOreVeinIds,
            node.appearance(),
            detectionState == DiscoveryState.HIDDEN && includeHidden && !scanInProgress && !sensorRevealed,
            scanInProgress,
            sensorRevealed);
    }

    private Set<CelestialObjectKey> temporaryVisibleKeys(CelestialObjectKey parentKey,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (parentKey == null || !parentKey.isRegistered()) return Set.of();
        return CelestialRegistry.get(parentKey.registeredBodyId())
            .filter(
                belt -> belt.properties()
                    .asteroidFieldProfile() != null)
            .map(belt -> {
                Set<CelestialObjectKey> keys = new LinkedHashSet<>();
                for (MinorCelestialBodyId id : scanTargets(parentKey, scanSnapshots)) {
                    keys.add(CelestialObjectKey.minorBody(id));
                }
                for (MinorCelestialBodyId id : sensorRevealTargets(belt, scanSnapshots)) {
                    keys.add(CelestialObjectKey.minorBody(id));
                }
                return Set.copyOf(keys);
            })
            .orElse(Set.of());
    }

    private static Set<MinorCelestialBodyId> scanTargets(CelestialObjectKey beltKey,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        if (!beltKey.isRegistered()) return Set.of();
        CelestialObjectId registeredBeltId = beltKey.registeredBodyId();
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
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null || !belt.key()
            .isRegistered()) return Set.of();
        CelestialObjectId beltId = belt.key()
            .registeredBodyId();
        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(beltId, profile);

        Set<MinorCelestialBodyId> targets = new LinkedHashSet<>();
        for (CelestialDiscoveryScanSnapshot scan : scanSnapshots) {
            if (scan.capability() != CelestialDiscoveryCapability.PROSPECTING || !scan.anchorKey()
                .isMinorBody()
                || scan.anchorKey()
                    .minorBodyId()
                    .parentBodyId() != beltId)
                continue;
            Optional<AsteroidFieldNode> anchor = AsteroidFieldResolver.findNode(
                beltId,
                profile,
                scan.anchorKey()
                    .minorBodyId()
                    .index());
            if (anchor.isEmpty()) continue;
            for (AsteroidFieldNode candidate : nodes) {
                if (isHidden(candidate)
                    && AsteroidFieldOrbitResolver.separation(profile, anchor.get(), candidate) <= scan.radius()) {
                    targets.add(candidate.id());
                }
            }
        }
        return Set.copyOf(targets);
    }

    private static boolean isHidden(AsteroidFieldNode node) {
        return CelestialKnowledgeClientState.effectiveDiscoveryState(CelestialObjectKey.minorBody(node.id()))
            == DiscoveryState.HIDDEN;
    }

}
