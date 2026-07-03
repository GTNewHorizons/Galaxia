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
    void prospectingAdvancesOreKnowledgeFromSignatureToProfile() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);

        knowledge.detect(medium.id());
        knowledge.detect(small.id());
        knowledge.prospect(small.id());

        assertEquals(
            AsteroidOreKnowledgeState.SIGNATURE,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());

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

        assertTrue(
            List.of(AsteroidOreKnowledgeState.SIGNATURE, AsteroidOreKnowledgeState.PROFILE)
                .contains(
                    knowledge.entryFor(large.id())
                        .oreKnowledgeState()));

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

    @Test
    void restoreReconcilesNewLoreAsteroidsWithoutDroppingExistingGeneratedKnowledge() {
        AsteroidFieldProfile oldProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();
        AsteroidFieldKnowledge oldKnowledge = AsteroidFieldKnowledge
            .initialize(CelestialObjectId.FROZEN_BELT, oldProfile);
        MinorCelestialBodyId generatedId = new MinorCelestialBodyId(
            CelestialObjectId.FROZEN_BELT,
            AsteroidSlotRanges.GENERATED_SLOT_MIN);
        oldKnowledge.prospect(generatedId);
        oldKnowledge.prospect(generatedId);

        AsteroidFieldProfile newProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .authoredAsteroid(1, AsteroidNodeKind.LORE, "Karnyx", AsteroidDetectionState.DETECTED)
            .build();

        AsteroidFieldKnowledge restored = AsteroidFieldKnowledge.fromSnapshot(
            CelestialObjectId.FROZEN_BELT,
            newProfile,
            oldKnowledge.snapshot(CelestialObjectId.FROZEN_BELT));

        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            restored.entryFor(generatedId)
                .oreKnowledgeState());
        assertEquals(
            AsteroidDetectionState.DETECTED,
            restored.entryFor(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 1))
                .detectionState());
    }

    @Test
    void restorePreservesSavedGeneratedAsteroidWhenCurrentProfileShrinks() {
        AsteroidFieldProfile oldProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(2, 0, 0)
            .radialBand(1.20, 1.40)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();
        AsteroidFieldKnowledge oldKnowledge = AsteroidFieldKnowledge
            .initialize(CelestialObjectId.FROZEN_BELT, oldProfile);

        AsteroidFieldProfile newProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();

        AsteroidFieldKnowledge restored = AsteroidFieldKnowledge.fromSnapshot(
            CelestialObjectId.FROZEN_BELT,
            newProfile,
            oldKnowledge.snapshot(CelestialObjectId.FROZEN_BELT));
        MinorCelestialBodyId savedId = new MinorCelestialBodyId(
            CelestialObjectId.FROZEN_BELT,
            AsteroidSlotRanges.GENERATED_SLOT_MIN + 1);

        assertEquals(
            oldKnowledge.entryFor(savedId)
                .detectionState(),
            restored.entryFor(savedId)
                .detectionState());
        assertEquals(
            oldKnowledge.entryFor(savedId)
                .oreKnowledgeState(),
            restored.entryFor(savedId)
                .oreKnowledgeState());
        assertTrue(
            restored.nodes()
                .stream()
                .anyMatch(
                    node -> node.id()
                        .equals(savedId)));
    }

    @Test
    void restorePreservesSavedAuthoredAsteroidWhenCurrentProfileOmitsIt() {
        AsteroidFieldProfile oldProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .authoredAsteroid(1, AsteroidNodeKind.LORE, "Karnyx", AsteroidDetectionState.DETECTED)
            .build();
        AsteroidFieldKnowledge oldKnowledge = AsteroidFieldKnowledge
            .initialize(CelestialObjectId.FROZEN_BELT, oldProfile);

        AsteroidFieldProfile newProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();
        MinorCelestialBodyId savedId = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 1);

        AsteroidFieldKnowledge restored = AsteroidFieldKnowledge.fromSnapshot(
            CelestialObjectId.FROZEN_BELT,
            newProfile,
            oldKnowledge.snapshot(CelestialObjectId.FROZEN_BELT));

        assertEquals(
            AsteroidDetectionState.DETECTED,
            restored.entryFor(savedId)
                .detectionState());
        assertTrue(
            restored.nodes()
                .stream()
                .anyMatch(
                    node -> node.id()
                        .equals(savedId)));
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
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", 1.0, List.of("ice", "sulfur")))
            .build();
    }
}
