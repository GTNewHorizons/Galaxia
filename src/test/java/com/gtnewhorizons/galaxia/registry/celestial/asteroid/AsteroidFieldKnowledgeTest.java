package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidFieldKnowledgeTest {

    @Test
    void initialKnowledgeDetectsOnlyLargeAsteroids() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());

        AsteroidFieldNode large = node(knowledge, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);

        assertEquals(
            AsteroidDetectionState.DETECTED,
            knowledge.entryFor(large.id())
                .detectionState());
        assertEquals(
            AsteroidDetectionState.HIDDEN,
            knowledge.entryFor(medium.id())
                .detectionState());
        assertEquals(
            AsteroidDetectionState.HIDDEN,
            knowledge.entryFor(small.id())
                .detectionState());
        assertEquals(
            AsteroidOreKnowledgeState.UNKNOWN,
            knowledge.entryFor(medium.id())
                .oreKnowledgeState());
        assertEquals(
            AsteroidOreKnowledgeState.UNKNOWN,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
    }

    @Test
    void detectionWorkBlocksProspectingUntilEveryAsteroidIsDetected() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);

        assertTrue(knowledge.hasDetectionWork());
        assertFalse(knowledge.canProspect());
        assertEquals(
            medium.id(),
            knowledge.nextDetectionCandidate()
                .orElseThrow()
                .id());
        assertThrows(IllegalStateException.class, () -> knowledge.prospect(small.id()));

        knowledge.detect(medium.id());

        assertTrue(knowledge.hasDetectionWork());
        assertEquals(
            small.id(),
            knowledge.nextDetectionCandidate()
                .orElseThrow()
                .id());
        assertThrows(IllegalStateException.class, () -> knowledge.prospect(small.id()));

        knowledge.detect(small.id());

        assertFalse(knowledge.hasDetectionWork());
        assertTrue(knowledge.canProspect());
        assertEquals(
            AsteroidDetectionState.DETECTED,
            knowledge.entryFor(small.id())
                .detectionState());
        assertEquals(
            AsteroidOreKnowledgeState.UNKNOWN,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
    }

    @Test
    void prospectingRevealsOreProfileForDetectedAsteroids() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);

        knowledge.detect(medium.id());
        knowledge.detect(small.id());
        knowledge.prospect(small.id());

        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
    }

    @Test
    void scopedProspectingOnlyWaitsForDetectionWorkInsideTheSameScope() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode large = node(knowledge, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        Predicate<AsteroidFieldNode> largeOnly = node -> node.id()
            .equals(large.id());
        Predicate<AsteroidFieldNode> largeAndMedium = node -> node.sizeClass() != AsteroidSizeClass.SMALL;

        assertFalse(knowledge.hasDetectionWork(largeOnly));
        assertTrue(knowledge.canProspect(largeOnly));
        assertEquals(
            large.id(),
            knowledge.nextProspectingCandidate(largeOnly)
                .orElseThrow()
                .id());

        knowledge.prospect(large.id(), largeOnly);

        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(large.id())
                .oreKnowledgeState());
        assertTrue(knowledge.hasDetectionWork(largeAndMedium));
        assertFalse(knowledge.canProspect(largeAndMedium));
        assertEquals(
            medium.id(),
            knowledge.nextDetectionCandidate(largeAndMedium)
                .orElseThrow()
                .id());
        assertFalse(
            knowledge.nextProspectingCandidate(largeAndMedium)
                .isPresent());
    }

    private static AsteroidFieldNode node(AsteroidFieldKnowledge knowledge, AsteroidSizeClass sizeClass) {
        return knowledge.nodes()
            .stream()
            .filter(node -> node.sizeClass() == sizeClass)
            .findFirst()
            .orElseThrow();
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 1, 1)
            .radialBand(1.20, 1.40)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();
    }
}
