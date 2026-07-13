package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public final class AsteroidStarmapProjectionBuilder {

    private AsteroidStarmapProjectionBuilder() {}

    public static List<AsteroidStarmapProjection> forBelt(@Nonnull CelestialObject belt,
        @Nonnull Optional<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshot, boolean includeHidden) {

        return forBelt(belt, knowledgeSnapshot, includeHidden, Set.of());
    }

    public static List<AsteroidStarmapProjection> forBelt(@Nonnull CelestialObject belt,
        @Nonnull Optional<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshot, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets) {

        return forBelt(belt, knowledgeSnapshot, includeHidden, scanTargets, Set.of());
    }

    public static List<AsteroidStarmapProjection> forBelt(@Nonnull CelestialObject belt,
        @Nonnull Optional<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshot, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets, @Nonnull Set<MinorCelestialBodyId> sensorRevealTargets) {

        Optional<AsteroidFieldKnowledgeSnapshot> snapshot = knowledgeSnapshot;
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
        snapshot.ifPresent(value -> {
            if (value.beltId() != beltId) {
                throw new IllegalArgumentException(
                    "Asteroid knowledge snapshot belt does not match projection belt: " + value.beltId()
                        + " != "
                        + beltId);
            }
        });

        AsteroidFieldNodeCatalog catalog = snapshot
            .map(value -> AsteroidFieldNodeCatalog.fromSnapshots(beltId, profile, value.nodeSnapshots()))
            .orElseGet(
                () -> AsteroidFieldNodeCatalog.restored(beltId)
                    .orElseGet(() -> AsteroidFieldNodeCatalog.fromGenerated(beltId, profile)));
        Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex = snapshot
            .map(AsteroidStarmapProjectionBuilder::entriesByIndex)
            .orElseGet(Map::of);

        return catalog.nodes()
            .stream()
            .map(
                node -> toProjection(
                    node,
                    profile,
                    entriesByIndex.get(node.index()),
                    includeHidden,
                    scanTargets,
                    sensorRevealTargets))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    public static List<AsteroidStarmapProjection> forBelt(@Nonnull CelestialObject belt,
        List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots, boolean includeHidden) {

        return forBelt(belt, knowledgeSnapshots, includeHidden, Set.of());
    }

    public static List<AsteroidStarmapProjection> forBelt(@Nonnull CelestialObject belt,
        List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets) {

        return forBelt(belt, knowledgeSnapshots, includeHidden, scanTargets, Set.of());
    }

    public static List<AsteroidStarmapProjection> forBelt(@Nonnull CelestialObject belt,
        List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots, boolean includeHidden,
        @Nonnull Set<MinorCelestialBodyId> scanTargets, @Nonnull Set<MinorCelestialBodyId> sensorRevealTargets) {

        CelestialObjectId beltId = belt.id()
            .registeredBodyId();
        Optional<AsteroidFieldKnowledgeSnapshot> snapshot = knowledgeSnapshots == null ? Optional.empty()
            : knowledgeSnapshots.stream()
                .filter(candidate -> candidate.beltId() == beltId)
                .findFirst();
        return forBelt(belt, snapshot, includeHidden, scanTargets, sensorRevealTargets);
    }

    private static Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex(
        AsteroidFieldKnowledgeSnapshot snapshot) {

        return snapshot.entries()
            .stream()
            .collect(Collectors.toUnmodifiableMap(AsteroidFieldKnowledgeSnapshot.Entry::index, Function.identity()));
    }

    private static Optional<AsteroidStarmapProjection> toProjection(AsteroidFieldNode node,
        AsteroidFieldProfile profile, AsteroidFieldKnowledgeSnapshot.Entry entry, boolean includeHidden,
        Set<MinorCelestialBodyId> scanTargets, Set<MinorCelestialBodyId> sensorRevealTargets) {

        DiscoveryState detectionState = entry == null ? node.initialDetectionState() : entry.detectionState();
        CelestialResourceKnowledgeState oreKnowledgeState = entry == null ? initialOreKnowledgeState(node)
            : entry.oreKnowledgeState();
        boolean scanInProgress = detectionState == DiscoveryState.HIDDEN && scanTargets.contains(node.id());
        boolean sensorRevealed = detectionState == DiscoveryState.HIDDEN && !scanInProgress
            && sensorRevealTargets.contains(node.id());
        if (detectionState == DiscoveryState.HIDDEN && !includeHidden && !scanInProgress && !sensorRevealed)
            return Optional.empty();

        Optional<String> visibleOreProfileId = oreKnowledgeState == CelestialResourceKnowledgeState.UNKNOWN
            ? Optional.empty()
            : Optional.of(
                node.oreProfile()
                    .id());
        List<String> visibleGtOreVeinIds = oreKnowledgeState == CelestialResourceKnowledgeState.PROFILE
            ? node.oreProfile()
                .gtOreVeinIds()
            : List.of();

        return Optional.of(
            new AsteroidStarmapProjection(
                AsteroidCelestialMaterializer.materialize(node, profile),
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
                sensorRevealed));
    }

    private static CelestialResourceKnowledgeState initialOreKnowledgeState(AsteroidFieldNode node) {
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
        return node.initialDetectionState() == DiscoveryState.DISCOVERED ? CelestialResourceKnowledgeState.SIGNATURE
            : CelestialResourceKnowledgeState.UNKNOWN;
    }
}
