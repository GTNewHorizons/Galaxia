package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

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
    void detectionAndProspectingAdvanceOneNodeAtATimeWithDetectionPriority() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();

        assertFalse(
            store.prospectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
                .isPresent());

        AsteroidFieldNode firstDetected = store.detectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
            .orElseThrow();
        AsteroidFieldNode secondDetected = store.detectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
            .orElseThrow();

        assertEquals(AsteroidSizeClass.MEDIUM, firstDetected.sizeClass());
        assertEquals(AsteroidSizeClass.SMALL, secondDetected.sizeClass());
        assertFalse(
            store.detectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
                .isPresent());

        AsteroidFieldNode prospected = store.prospectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
            .orElseThrow();
        AsteroidFieldKnowledge knowledge = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);

        assertEquals(
            AsteroidOreKnowledgeState.SIGNATURE,
            knowledge.entryFor(prospected.id())
                .oreKnowledgeState());
        assertEquals(
            prospected.id(),
            store.prospectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
                .orElseThrow()
                .id());
        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(prospected.id())
                .oreKnowledgeState());
    }

    @Test
    void scopedProspectingOnlyAdvancesNodesInsideScope() {
        AsteroidFieldKnowledgeStore store = new AsteroidFieldKnowledgeStore();
        AsteroidFieldProfile profile = profile();
        AsteroidFieldKnowledge knowledge = store.getOrCreate(TEAM_A, CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldNode large = knowledge.nodes()
            .stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.LARGE)
            .findFirst()
            .orElseThrow();
        Predicate<AsteroidFieldNode> largeOnly = node -> node.id()
            .equals(large.id());

        AsteroidFieldNode prospected = store.prospectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile, largeOnly)
            .orElseThrow();

        assertEquals(large.id(), prospected.id());
        assertEquals(
            AsteroidOreKnowledgeState.SIGNATURE,
            knowledge.entryFor(large.id())
                .oreKnowledgeState());
        assertEquals(
            large.id(),
            store.prospectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile, largeOnly)
                .orElseThrow()
                .id());
        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(large.id())
                .oreKnowledgeState());
        assertTrue(
            store.detectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile, largeOnly)
                .isEmpty());
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
        AsteroidFieldNode detected = store.detectNext(TEAM_A, CelestialObjectId.FROZEN_BELT, profile)
            .orElseThrow();
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
        assertEquals(AsteroidDetectionState.DETECTED, entry.detectionState());
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .seedSalt(41L)
            .generationVersion(1)
            .sizeCounts(1, 1, 1)
            .radialBand(1.20, 1.40)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();
    }
}
