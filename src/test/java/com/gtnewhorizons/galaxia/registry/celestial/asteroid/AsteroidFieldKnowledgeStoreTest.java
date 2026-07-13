package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidFieldKnowledgeStoreTest {

    private static final UUID TEAM_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TEAM_B = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Test
    void storeKeepsSeparateKnowledgePerTeamAndBelt() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();

        AsteroidFieldKnowledge first = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldKnowledge again = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldKnowledge otherTeam = store.getOrCreate(TEAM_B, CelestialObjectId.FROZEN_BELT, profile);

        assertSame(first, again);
        assertNotSame(first, otherTeam);
    }

    @Test
    void detectionWorkTakesPriorityOverProspecting() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();
        AsteroidFieldKnowledge knowledge = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);

        assertFalse(nextWork(knowledge, node -> true, CelestialDiscoveryStep.SIGNATURE).isPresent());

        while (nextWork(knowledge, node -> true, CelestialDiscoveryStep.DETECTION).isPresent()) {
            AsteroidFieldNode detected = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
                .orElseThrow();
            assertEquals(
                CelestialResourceKnowledgeState.UNKNOWN,
                knowledge.entryFor(detected.id())
                    .oreKnowledgeState());
        }

        assertTrue(nextWork(knowledge, node -> true, CelestialDiscoveryStep.SIGNATURE).isPresent());
    }

    @Test
    void eachAdvanceCompletesOneDiscoveryUnit() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();
        AsteroidFieldKnowledge knowledge = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);

        AsteroidFieldNode firstDetected = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
            .orElseThrow();
        assertEquals(1, detectedUnknownCount(knowledge));

        AsteroidFieldNode secondDetected = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
            .orElseThrow();
        assertFalse(
            firstDetected.id()
                .equals(secondDetected.id()));
        assertEquals(2, detectedUnknownCount(knowledge));

        AsteroidFieldNode prospected = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile).orElseThrow();

        assertEquals(
            CelestialResourceKnowledgeState.SIGNATURE,
            knowledge.entryFor(prospected.id())
                .oreKnowledgeState());

        while (hasUnknownDetectedAsteroid(knowledge)) {
            AsteroidFieldNode signature = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
                .orElseThrow();
            assertEquals(
                CelestialResourceKnowledgeState.SIGNATURE,
                knowledge.entryFor(signature.id())
                    .oreKnowledgeState());
        }

        assertEquals(
            prospected.id(),
            revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile).orElseThrow()
                .id());
        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            knowledge.entryFor(prospected.id())
                .oreKnowledgeState());
    }

    @Test
    void scopedProspectingOnlyAdvancesNodesInsideScope() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profileWithScopedProspectingTarget();
        AsteroidFieldKnowledge knowledge = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldNode large = knowledge.nodes()
            .stream()
            .filter(node -> node.kind() == AsteroidNodeKind.LORE)
            .findFirst()
            .orElseThrow();
        Predicate<AsteroidFieldNode> largeOnly = node -> node.id()
            .equals(large.id());

        AsteroidFieldNode prospected = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile, largeOnly)
            .orElseThrow();

        assertEquals(large.id(), prospected.id());
        assertTrue(
            List.of(CelestialResourceKnowledgeState.SIGNATURE, CelestialResourceKnowledgeState.PROFILE)
                .contains(
                    knowledge.entryFor(large.id())
                        .oreKnowledgeState()));
        assertEquals(
            large.id(),
            revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile, largeOnly).orElseThrow()
                .id());
        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            knowledge.entryFor(large.id())
                .oreKnowledgeState());
        assertTrue(nextWork(knowledge, largeOnly, CelestialDiscoveryStep.DETECTION).isEmpty());
    }

    @Test
    void clearDropsAllTeamKnowledge() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();
        AsteroidFieldKnowledge first = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);

        store.clear();

        assertTrue(
            store.get(TEAM_A, CelestialObjectId.FROZEN_BELT)
                .isEmpty());
        assertNotSame(first, store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile));
    }

    @Test
    void snapshotsExposeKnownStateForOneTeam() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();
        AsteroidFieldNode detected = revealNext(store, TEAM_A, CelestialObjectId.FROZEN_BELT, profile).orElseThrow();
        store.getOrCreate(TEAM_B, CelestialObjectId.FROZEN_BELT, profile);

        List<AsteroidFieldKnowledgeSnapshot> snapshots = store.snapshots(TEAM_A);

        assertEquals(1, snapshots.size());
        AsteroidFieldKnowledgeSnapshot snapshot = snapshots.get(0);
        assertEquals(CelestialObjectId.FROZEN_BELT, snapshot.beltId());
        AsteroidFieldKnowledgeSnapshot.Entry entry = snapshot.entries()
            .stream()
            .filter(candidate -> candidate.index() == detected.index())
            .findFirst()
            .orElseThrow();
        assertEquals(DiscoveryState.DISCOVERED, entry.detectionState());
    }

    private static Optional<AsteroidFieldNode> revealNext(AsteroidFieldKnowledgeStore store, UUID teamId,
        CelestialObjectId beltId, AsteroidFieldProfile profile) {
        return revealNext(store, teamId, beltId, profile, node -> true);
    }

    private static Optional<AsteroidFieldNode> revealNext(AsteroidFieldKnowledgeStore store, UUID teamId,
        CelestialObjectId beltId, AsteroidFieldProfile profile, Predicate<AsteroidFieldNode> scope) {
        AsteroidFieldKnowledge knowledge = store.getOrCreate(teamId, beltId, profile);
        AsteroidFieldScanContext context = context(scope);
        Optional<CelestialDiscoveryWork> work = knowledge.nextDiscoveryWork(context);
        work.ifPresent(discovery -> knowledge.revealDiscovery(discovery, context));
        return work.map(AsteroidFieldKnowledgeStoreTest::asteroidId)
            .map(
                id -> knowledge.nodes()
                    .stream()
                    .filter(
                        node -> node.id()
                            .equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Discovery work targeted unknown asteroid: " + id)));
    }

    private static MinorCelestialBodyId asteroidId(CelestialDiscoveryWork work) {
        if (work instanceof AsteroidFieldDiscoveryWork asteroidWork) return asteroidWork.asteroidId();
        throw new IllegalArgumentException("Expected asteroid field discovery work");
    }

    private static Optional<CelestialDiscoveryWork> nextWork(AsteroidFieldKnowledge knowledge,
        Predicate<AsteroidFieldNode> scope, CelestialDiscoveryStep pass) {
        return knowledge.nextDiscoveryWork(context(scope))
            .filter(work -> work.step() == pass);
    }

    private static AsteroidFieldScanContext context(Predicate<AsteroidFieldNode> scope) {
        return new AsteroidFieldScanContext(scope, AsteroidFieldScanOrder.byIndex());
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .seedSalt(41L)
            .generationVersion(1)
            .sizeCounts(1, 1, 1)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .build();
    }

    private static AsteroidFieldProfile profileWithScopedProspectingTarget() {
        return AsteroidFieldProfile.builder()
            .seedSalt(41L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .authoredAsteroid(
                new AuthoredAsteroidDefinition(
                    1,
                    AsteroidNodeKind.LORE,
                    "scoped_target",
                    "Scoped Target",
                    AsteroidSizeClass.LARGE,
                    DiscoveryState.DISCOVERED,
                    CelestialResourceKnowledgeState.UNKNOWN,
                    0.0,
                    0.5,
                    null,
                    null))
            .build();
    }

    private static boolean hasUnknownDetectedAsteroid(AsteroidFieldKnowledge knowledge) {
        return knowledge.nodes()
            .stream()
            .anyMatch(node -> isDetectedUnknown(knowledge, node));
    }

    private static long detectedUnknownCount(AsteroidFieldKnowledge knowledge) {
        return knowledge.nodes()
            .stream()
            .filter(node -> isDetectedUnknown(knowledge, node))
            .count();
    }

    private static boolean isDetectedUnknown(AsteroidFieldKnowledge knowledge, AsteroidFieldNode node) {
        return knowledge.entryFor(node.id())
            .detectionState() == DiscoveryState.DISCOVERED
            && knowledge.entryFor(node.id())
                .oreKnowledgeState() == CelestialResourceKnowledgeState.UNKNOWN;
    }
}
