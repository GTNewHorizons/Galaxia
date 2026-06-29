package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class AsteroidFieldKnowledge {

    private final List<AsteroidFieldNode> nodes;
    private final Map<MinorCelestialBodyId, Entry> entriesById;

    private AsteroidFieldKnowledge(List<AsteroidFieldNode> nodes, Map<MinorCelestialBodyId, Entry> entriesById) {
        this.nodes = List.copyOf(nodes);
        this.entriesById = new LinkedHashMap<>(entriesById);
    }

    public static AsteroidFieldKnowledge initialize(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(beltId, profile);
        Map<MinorCelestialBodyId, Entry> entries = new LinkedHashMap<>();
        for (AsteroidFieldNode node : nodes) {
            entries.put(
                node.id(),
                new Entry(
                    AsteroidFieldResolver.initialDetectionState(node),
                    AsteroidFieldResolver.initialOreKnowledge(node)));
        }
        return new AsteroidFieldKnowledge(nodes, entries);
    }

    public static AsteroidFieldKnowledge fromSnapshot(CelestialObjectId beltId, AsteroidFieldProfile profile,
        AsteroidFieldKnowledgeSnapshot snapshot) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        if (snapshot.beltId() != beltId) {
            throw new IllegalStateException(
                "Asteroid snapshot belt does not match requested belt: " + snapshot.beltId());
        }

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(beltId, profile);
        if (snapshot.entries()
            .size() != nodes.size()) {
            throw new IllegalStateException("Asteroid snapshot entry count does not match profile for " + beltId);
        }

        Map<MinorCelestialBodyId, Entry> entries = new LinkedHashMap<>();
        boolean[] seen = new boolean[nodes.size()];
        for (AsteroidFieldKnowledgeSnapshot.Entry snapshotEntry : snapshot.entries()) {
            int index = snapshotEntry.index();
            if (index < 0 || index >= nodes.size()) {
                throw new IllegalStateException(
                    "Asteroid snapshot index is outside profile for " + beltId + ": " + index);
            }
            if (seen[index]) {
                throw new IllegalStateException(
                    "Asteroid snapshot contains duplicate index for " + beltId + ": " + index);
            }
            seen[index] = true;
            if (snapshotEntry.detectionState() == AsteroidDetectionState.HIDDEN
                && snapshotEntry.oreKnowledgeState() != AsteroidOreKnowledgeState.UNKNOWN) {
                throw new IllegalStateException("Hidden asteroid snapshot entry cannot expose ore knowledge: " + index);
            }
            AsteroidFieldNode node = nodes.get(index);
            entries.put(node.id(), new Entry(snapshotEntry.detectionState(), snapshotEntry.oreKnowledgeState()));
        }
        return new AsteroidFieldKnowledge(nodes, entries);
    }

    public List<AsteroidFieldNode> nodes() {
        return nodes;
    }

    public Entry entryFor(MinorCelestialBodyId id) {
        Entry entry = entriesById.get(Objects.requireNonNull(id, "id cannot be null"));
        if (entry == null) {
            throw new IllegalArgumentException("Unknown asteroid node id: " + id);
        }
        return entry;
    }

    public Optional<AsteroidFieldNode> nextDetectionCandidate() {
        return nextDetectionCandidate(node -> true);
    }

    public Optional<AsteroidFieldNode> nextDetectionCandidate(Predicate<AsteroidFieldNode> scope) {
        return nextDetectionCandidate(scope, AsteroidFieldScanOrder.byIndex());
    }

    public Optional<AsteroidFieldNode> nextDetectionCandidate(Predicate<AsteroidFieldNode> scope,
        Comparator<AsteroidFieldNode> order) {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        return scopedNodes(scope, order)
            .filter(node -> entryFor(node.id()).detectionState() == AsteroidDetectionState.HIDDEN)
            .findFirst();
    }

    public boolean hasDetectionWork() {
        return hasDetectionWork(node -> true);
    }

    public boolean hasDetectionWork(Predicate<AsteroidFieldNode> scope) {
        return nextDetectionCandidate(scope).isPresent();
    }

    public boolean canProspect() {
        return canProspect(node -> true);
    }

    public boolean canProspect(Predicate<AsteroidFieldNode> scope) {
        return !hasDetectionWork(scope);
    }

    public Optional<AsteroidFieldNode> nextProspectingCandidate() {
        return nextProspectingCandidate(node -> true);
    }

    public Optional<AsteroidFieldNode> nextProspectingCandidate(Predicate<AsteroidFieldNode> scope) {
        return nextProspectingCandidate(scope, AsteroidFieldScanOrder.byIndex());
    }

    public Optional<AsteroidFieldNode> nextProspectingCandidate(Predicate<AsteroidFieldNode> scope,
        Comparator<AsteroidFieldNode> order) {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        if (!canProspect(scope)) return Optional.empty();
        Optional<AsteroidFieldNode> signatureCandidate = nextSignatureCandidate(scope, order);
        return signatureCandidate.isPresent() ? signatureCandidate : nextProfileCandidate(scope, order);
    }

    public Optional<AsteroidFieldNode> nextSignatureCandidate(Predicate<AsteroidFieldNode> scope,
        Comparator<AsteroidFieldNode> order) {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        if (!canProspect(scope)) return Optional.empty();
        return scopedNodes(scope, order)
            .filter(node -> entryFor(node.id()).detectionState() == AsteroidDetectionState.DETECTED)
            .filter(node -> entryFor(node.id()).oreKnowledgeState() == AsteroidOreKnowledgeState.UNKNOWN)
            .findFirst();
    }

    public Optional<AsteroidFieldNode> nextProfileCandidate(Predicate<AsteroidFieldNode> scope,
        Comparator<AsteroidFieldNode> order) {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        if (!canProspect(scope) || nextSignatureCandidate(scope, order).isPresent()) return Optional.empty();
        return scopedNodes(scope, order)
            .filter(node -> entryFor(node.id()).detectionState() == AsteroidDetectionState.DETECTED)
            .filter(node -> entryFor(node.id()).oreKnowledgeState() == AsteroidOreKnowledgeState.SIGNATURE)
            .findFirst();
    }

    public Entry detect(MinorCelestialBodyId id) {
        AsteroidFieldNode node = requireNode(id);
        Entry current = entryFor(id);
        if (current.detectionState() == AsteroidDetectionState.DETECTED) return current;

        Entry updated = new Entry(
            AsteroidDetectionState.DETECTED,
            AsteroidFieldResolver.oreKnowledgeAfterDetection(node));
        entriesById.put(id, updated);
        return updated;
    }

    public Entry prospect(MinorCelestialBodyId id) {
        return prospect(id, node -> true);
    }

    public Entry prospect(MinorCelestialBodyId id, Predicate<AsteroidFieldNode> scope) {
        AsteroidFieldNode node = requireNode(id);
        Objects.requireNonNull(scope, "scope cannot be null");
        if (!scope.test(node)) {
            throw new IllegalArgumentException("Asteroid node is outside prospecting scope: " + id);
        }
        Entry current = entryFor(id);
        if (current.detectionState() == AsteroidDetectionState.HIDDEN) {
            throw new IllegalStateException("Cannot prospect hidden asteroid node: " + id);
        }
        if (hasDetectionWork(scope)) {
            throw new IllegalStateException("Asteroid detection must finish before prospecting can start");
        }

        Entry updated = new Entry(AsteroidDetectionState.DETECTED, nextOreKnowledgeState(current.oreKnowledgeState()));
        entriesById.put(id, updated);
        return updated;
    }

    private static AsteroidOreKnowledgeState nextOreKnowledgeState(AsteroidOreKnowledgeState current) {
        return switch (current) {
            case UNKNOWN -> AsteroidOreKnowledgeState.SIGNATURE;
            case SIGNATURE, PROFILE -> AsteroidOreKnowledgeState.PROFILE;
        };
    }

    private Stream<AsteroidFieldNode> scopedNodes(Predicate<AsteroidFieldNode> scope,
        Comparator<AsteroidFieldNode> order) {
        return nodes.stream()
            .filter(scope)
            .sorted(order);
    }

    public AsteroidFieldKnowledgeSnapshot snapshot(CelestialObjectId beltId) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        return new AsteroidFieldKnowledgeSnapshot(
            beltId,
            nodes.stream()
                .map(node -> {
                    Entry entry = entryFor(node.id());
                    return new AsteroidFieldKnowledgeSnapshot.Entry(
                        node.index(),
                        entry.detectionState(),
                        entry.oreKnowledgeState());
                })
                .toList());
    }

    private AsteroidFieldNode requireNode(MinorCelestialBodyId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return nodes.stream()
            .filter(
                node -> node.id()
                    .equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown asteroid node id: " + id));
    }

    public record Entry(AsteroidDetectionState detectionState, AsteroidOreKnowledgeState oreKnowledgeState) {

        public Entry {
            detectionState = Objects.requireNonNull(detectionState, "detectionState cannot be null");
            oreKnowledgeState = Objects.requireNonNull(oreKnowledgeState, "oreKnowledgeState cannot be null");
        }
    }
}
