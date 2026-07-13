package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidFieldKnowledgeTest {

    @Test
    void initialKnowledgeDetectsOnlyLargeAsteroids() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());

        AsteroidFieldNode large = node(knowledge, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);

        assertEquals(
            DiscoveryState.DISCOVERED,
            knowledge.entryFor(large.id())
                .detectionState());
        assertEquals(
            DiscoveryState.HIDDEN,
            knowledge.entryFor(medium.id())
                .detectionState());
        assertEquals(
            DiscoveryState.HIDDEN,
            knowledge.entryFor(small.id())
                .detectionState());
        assertEquals(
            CelestialResourceKnowledgeState.UNKNOWN,
            knowledge.entryFor(medium.id())
                .oreKnowledgeState());
        assertEquals(
            CelestialResourceKnowledgeState.UNKNOWN,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
    }

    @Test
    void snapshotDoesNotExposeHiddenAsteroidIndexes() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode hidden = node(knowledge, AsteroidSizeClass.MEDIUM);

        AsteroidFieldKnowledgeSnapshot snapshot = knowledge.snapshot(CelestialObjectId.FROZEN_BELT);

        assertTrue(
            snapshot.entries()
                .stream()
                .allMatch(entry -> entry.detectionState() == DiscoveryState.DISCOVERED));
        assertFalse(
            snapshot.entries()
                .stream()
                .anyMatch(entry -> entry.index() == hidden.index()));
    }

    @Test
    void detectionWorkBlocksProspectingUntilEveryAsteroidIsDetected() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);
        AsteroidFieldScanContext allByIndex = context(node -> true);

        CelestialDiscoveryWork firstWork = knowledge.nextDiscoveryWork(allByIndex)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.DETECTION, firstWork.step());

        knowledge.revealDiscovery(firstWork, allByIndex);

        CelestialDiscoveryWork secondWork = knowledge.nextDiscoveryWork(allByIndex)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.DETECTION, secondWork.step());
        assertEquals(
            Set.of(CelestialObjectKey.minorBody(medium.id()), CelestialObjectKey.minorBody(small.id())),
            Set.of(firstWork.targetKey(), secondWork.targetKey()));

        knowledge.revealDiscovery(secondWork, allByIndex);

        CelestialDiscoveryWork prospectingWork = knowledge.nextDiscoveryWork(allByIndex)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.SIGNATURE, prospectingWork.step());
        assertEquals(
            DiscoveryState.DISCOVERED,
            knowledge.entryFor(small.id())
                .detectionState());
        assertEquals(
            CelestialResourceKnowledgeState.UNKNOWN,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
    }

    @Test
    void prospectingAdvancesOreKnowledgeFromSignatureToProfile() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);
        AsteroidFieldScanContext smallOnly = context(
            node -> node.id()
                .equals(small.id()));

        revealNext(knowledge, smallOnly, CelestialDiscoveryStep.DETECTION);
        revealNext(knowledge, smallOnly, CelestialDiscoveryStep.SIGNATURE);

        assertEquals(
            CelestialResourceKnowledgeState.SIGNATURE,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());

        revealNext(knowledge, smallOnly, CelestialDiscoveryStep.PROFILE);

        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
    }

    @Test
    void discoveryWorkOwnsUncoveredFactAndKnowledgeMutation() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);
        Predicate<AsteroidFieldNode> smallOnly = node -> node.id()
            .equals(small.id());
        AsteroidFieldScanContext context = context(smallOnly);

        CelestialDiscoveryWork detection = knowledge.nextDiscoveryWork(context)
            .orElseThrow();

        assertEquals(CelestialDiscoveryStep.DETECTION, detection.step());
        assertEquals(CelestialObjectKey.minorBody(small.id()), detection.targetKey());
        knowledge.revealDiscovery(detection, context);

        CelestialDiscoveryWork signature = knowledge.nextDiscoveryWork(context)
            .orElseThrow();

        assertEquals(CelestialDiscoveryStep.SIGNATURE, signature.step());
        assertEquals(
            CelestialResourceKnowledgeState.UNKNOWN,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
        knowledge.revealDiscovery(signature, context);

        CelestialDiscoveryWork profile = knowledge.nextDiscoveryWork(context)
            .orElseThrow();

        assertEquals(CelestialDiscoveryStep.PROFILE, profile.step());
        assertEquals(
            CelestialResourceKnowledgeState.SIGNATURE,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
        knowledge.revealDiscovery(profile, context);

        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            knowledge.entryFor(small.id())
                .oreKnowledgeState());
        assertFalse(
            knowledge.nextDiscoveryWork(context)
                .isPresent());
    }

    @Test
    void scopedProspectingOnlyWaitsForDetectionWorkInsideTheSameScope() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode large = node(knowledge, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        Predicate<AsteroidFieldNode> largeOnly = node -> node.id()
            .equals(large.id());
        Predicate<AsteroidFieldNode> largeAndMedium = node -> node.sizeClass() != AsteroidSizeClass.SMALL;
        AsteroidFieldScanContext largeOnlyContext = context(largeOnly);
        AsteroidFieldScanContext largeAndMediumContext = context(largeAndMedium);

        CelestialDiscoveryWork largeSignature = knowledge.nextDiscoveryWork(largeOnlyContext)
            .orElseThrow();
        assertTrue(
            List.of(CelestialDiscoveryStep.SIGNATURE, CelestialDiscoveryStep.PROFILE)
                .contains(largeSignature.step()));
        assertEquals(CelestialObjectKey.minorBody(large.id()), largeSignature.targetKey());

        knowledge.revealDiscovery(largeSignature, largeOnlyContext);

        assertTrue(
            List.of(CelestialResourceKnowledgeState.SIGNATURE, CelestialResourceKnowledgeState.PROFILE)
                .contains(
                    knowledge.entryFor(large.id())
                        .oreKnowledgeState()));

        revealUntilProfile(knowledge, largeOnlyContext, large.id());

        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            knowledge.entryFor(large.id())
                .oreKnowledgeState());
        CelestialDiscoveryWork mediumDetection = knowledge.nextDiscoveryWork(largeAndMediumContext)
            .orElseThrow();
        assertEquals(CelestialDiscoveryStep.DETECTION, mediumDetection.step());
        assertEquals(CelestialObjectKey.minorBody(medium.id()), mediumDetection.targetKey());
    }

    @Test
    void restoreReconcilesNewLoreAsteroidsWithoutDroppingExistingGeneratedKnowledge() {
        AsteroidFieldProfile oldProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .build();
        AsteroidFieldKnowledge oldKnowledge = AsteroidFieldKnowledge
            .initialize(CelestialObjectId.FROZEN_BELT, oldProfile);
        MinorCelestialBodyId generatedId = new MinorCelestialBodyId(
            CelestialObjectId.FROZEN_BELT,
            AsteroidSlotRanges.GENERATED_SLOT_MIN);
        AsteroidFieldScanContext generatedOnly = context(
            node -> node.id()
                .equals(generatedId));
        revealUntilProfile(oldKnowledge, generatedOnly, generatedId);

        AsteroidFieldProfile newProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .authoredAsteroid(1, AsteroidNodeKind.LORE, "Karnyx", DiscoveryState.DISCOVERED)
            .build();

        AsteroidFieldKnowledge restored = AsteroidFieldKnowledge.fromSnapshot(
            CelestialObjectId.FROZEN_BELT,
            newProfile,
            oldKnowledge.snapshot(CelestialObjectId.FROZEN_BELT));

        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            restored.entryFor(generatedId)
                .oreKnowledgeState());
        assertEquals(
            DiscoveryState.DISCOVERED,
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
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .build();
        AsteroidFieldKnowledge oldKnowledge = AsteroidFieldKnowledge
            .initialize(CelestialObjectId.FROZEN_BELT, oldProfile);

        AsteroidFieldProfile newProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
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
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .authoredAsteroid(1, AsteroidNodeKind.LORE, "Karnyx", DiscoveryState.DISCOVERED)
            .build();
        AsteroidFieldKnowledge oldKnowledge = AsteroidFieldKnowledge
            .initialize(CelestialObjectId.FROZEN_BELT, oldProfile);

        AsteroidFieldProfile newProfile = AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .build();
        MinorCelestialBodyId savedId = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 1);

        AsteroidFieldKnowledge restored = AsteroidFieldKnowledge.fromSnapshot(
            CelestialObjectId.FROZEN_BELT,
            newProfile,
            oldKnowledge.snapshot(CelestialObjectId.FROZEN_BELT));

        assertEquals(
            DiscoveryState.DISCOVERED,
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

    private static void revealNext(AsteroidFieldKnowledge knowledge, AsteroidFieldScanContext context,
        CelestialDiscoveryStep expectedPass) {
        CelestialDiscoveryWork work = knowledge.nextDiscoveryWork(context)
            .orElseThrow();
        assertEquals(expectedPass, work.step());
        knowledge.revealDiscovery(work, context);
    }

    private static void revealUntilProfile(AsteroidFieldKnowledge knowledge, AsteroidFieldScanContext context,
        MinorCelestialBodyId id) {
        while (knowledge.entryFor(id)
            .oreKnowledgeState() != CelestialResourceKnowledgeState.PROFILE) {
            CelestialDiscoveryWork work = knowledge.nextDiscoveryWork(context)
                .orElseThrow();
            knowledge.revealDiscovery(work, context);
        }
    }

    private static AsteroidFieldScanContext context(Predicate<AsteroidFieldNode> scope) {
        return new AsteroidFieldScanContext(scope, AsteroidFieldScanOrder.byIndex());
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .seedSalt(31L)
            .generationVersion(1)
            .sizeCounts(1, 1, 1)
            .radialBand(1.20, 1.40)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("volatile_ice", List.of("ice", "sulfur")))
            .build();
    }
}
