package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

/**
 * Decorates canonical asteroid bodies already returned by Registry children.
 * Does not enumerate the field catalog or decide child-list membership.
 */
public final class AsteroidStarmapProjectionBuilder {

    private AsteroidStarmapProjectionBuilder() {}

    public static List<AsteroidStarmapProjection> decorate(@Nonnull CelestialObject belt,
        @Nonnull List<CelestialObject> canonicalBodies,
        @Nonnull Optional<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshot, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets, @Nonnull Set<MinorCelestialBodyId> sensorRevealTargets) {

        if (!belt.id()
            .isRegistered()) {
            throw new IllegalArgumentException("Asteroid starmap projection requires a registered belt body");
        }
        CelestialObjectId beltId = belt.id()
            .registeredBodyId();
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) {
            throw new IllegalArgumentException("Asteroid starmap projection requires an asteroid field profile");
        }
        knowledgeSnapshot.ifPresent(value -> {
            if (value.beltId() != beltId) {
                throw new IllegalArgumentException(
                    "Asteroid knowledge snapshot belt does not match projection belt: " + value.beltId()
                        + " != "
                        + beltId);
            }
        });

        AsteroidFieldNodeCatalog catalog = knowledgeSnapshot
            .map(value -> AsteroidFieldNodeCatalog.fromSnapshots(beltId, profile, value.nodeSnapshots()))
            .orElseGet(
                () -> AsteroidFieldNodeCatalog.restored(beltId)
                    .orElseGet(() -> AsteroidFieldNodeCatalog.fromGenerated(beltId, profile)));
        Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex = knowledgeSnapshot
            .map(AsteroidStarmapProjectionBuilder::entriesByIndex)
            .orElseGet(Map::of);

        List<AsteroidStarmapProjection> projections = new ArrayList<>();
        for (CelestialObject body : canonicalBodies) {
            if (body == null || !body.id()
                .isMinorBody()) continue;
            MinorCelestialBodyId minorId = body.id()
                .minorBodyId();
            if (minorId.parentBodyId() != beltId) continue;
            Optional<AsteroidFieldNode> node = catalog.resolve(minorId);
            if (node.isEmpty()) continue;
            projections.add(
                toProjection(
                    body,
                    node.get(),
                    entriesByIndex.get(
                        node.get()
                            .index()),
                    includeHidden,
                    scanTargets,
                    sensorRevealTargets));
        }
        return List.copyOf(projections);
    }

    public static List<AsteroidStarmapProjection> decorate(@Nonnull CelestialObject belt,
        @Nonnull List<CelestialObject> canonicalBodies, List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots,
        boolean includeHidden, @Nonnull Set<MinorCelestialBodyId> scanTargets,
        @Nonnull Set<MinorCelestialBodyId> sensorRevealTargets) {

        CelestialObjectId beltId = belt.id()
            .registeredBodyId();
        Optional<AsteroidFieldKnowledgeSnapshot> snapshot = knowledgeSnapshots == null ? Optional.empty()
            : knowledgeSnapshots.stream()
                .filter(candidate -> candidate.beltId() == beltId)
                .findFirst();
        return decorate(belt, canonicalBodies, snapshot, includeHidden, scanTargets, sensorRevealTargets);
    }

    private static Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex(
        AsteroidFieldKnowledgeSnapshot snapshot) {

        return snapshot.entries()
            .stream()
            .collect(Collectors.toUnmodifiableMap(AsteroidFieldKnowledgeSnapshot.Entry::index, Function.identity()));
    }

    private static AsteroidStarmapProjection toProjection(CelestialObject body, AsteroidFieldNode node,
        AsteroidFieldKnowledgeSnapshot.Entry entry, boolean includeHidden, Set<MinorCelestialBodyId> scanTargets,
        Set<MinorCelestialBodyId> sensorRevealTargets) {

        DiscoveryState detectionState = entry == null ? node.initialDetectionState() : entry.detectionState();
        CelestialResourceKnowledgeState oreKnowledgeState = entry == null ? initialOreKnowledgeState(node)
            : entry.oreKnowledgeState();
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

    private static CelestialResourceKnowledgeState initialOreKnowledgeState(AsteroidFieldNode node) {
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
        return node.initialDetectionState() == DiscoveryState.DISCOVERED ? CelestialResourceKnowledgeState.SIGNATURE
            : CelestialResourceKnowledgeState.UNKNOWN;
    }
}
