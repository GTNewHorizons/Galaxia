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

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

/**
 * Client presentation adapter for asteroid starmap ghosts and decoration.
 * Supplies temporary scan/sensor visibility to DiscoveryView and decorates
 * canonical Registry children — never owns a second child list.
 */
public final class AsteroidClientProjectionService {

    private final Map<CelestialObjectKey, CachedProjections> cache = new LinkedHashMap<>();
    private boolean includeHidden;

    private record CachedProjections(List<CelestialObjectKey> canonicalChildKeys,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots,
        Map<CelestialObjectKey, CelestialKnowledgeFacts> factsSnapshot, boolean includeHidden,
        List<AsteroidStarmapProjection> projections, Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId) {

        boolean matches(List<CelestialObjectKey> currentChildKeys, List<CelestialDiscoveryScanSnapshot> currentScans,
            Map<CelestialObjectKey, CelestialKnowledgeFacts> currentFacts, boolean currentIncludeHidden) {
            return canonicalChildKeys.equals(currentChildKeys) && scanSnapshots.equals(currentScans)
                && factsSnapshot.equals(currentFacts)
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
        boolean sibling = canonicalSiblings.stream()
            .anyMatch(
                candidate -> candidate.key()
                    .equals(body.key()));
        if (!sibling) return Optional.empty();
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
        List<CelestialObjectKey> childKeys = canonicalSiblings.stream()
            .map(CelestialObject::key)
            .toList();
        Map<CelestialObjectKey, CelestialKnowledgeFacts> factsSnapshot = factsForChildren(childKeys);
        CachedProjections cached = cache.get(belt.key());
        if (cached != null && cached.matches(childKeys, scanSnapshots, factsSnapshot, includeHidden)) return cached;

        Set<MinorCelestialBodyId> scanTargets = scanTargets(belt.key(), scanSnapshots);
        Set<MinorCelestialBodyId> sensorRevealTargets = sensorRevealTargets(belt, scanSnapshots);
        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .decorate(belt, canonicalSiblings, includeHidden, scanTargets, sensorRevealTargets);
        Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId = projections.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    projection -> projection.body()
                        .key(),
                    Function.identity()));
        CachedProjections rebuilt = new CachedProjections(
            childKeys,
            scanSnapshots,
            factsSnapshot,
            includeHidden,
            projections,
            byBodyId);
        cache.put(belt.key(), rebuilt);
        return rebuilt;
    }

    private static Map<CelestialObjectKey, CelestialKnowledgeFacts> factsForChildren(
        List<CelestialObjectKey> childKeys) {
        Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = new LinkedHashMap<>();
        for (CelestialObjectKey key : childKeys) {
            CelestialKnowledgeClientState.facts(key)
                .ifPresent(value -> facts.put(key, value));
        }
        return Map.copyOf(facts);
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
