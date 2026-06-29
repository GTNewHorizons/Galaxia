package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidFieldStarmapViewTest {

    @Test
    void visibleEntriesOnlyExposeDetectedAsteroidsInFieldOrder() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.initialize(CelestialObjectId.FROZEN_BELT, profile());
        AsteroidFieldNode large = node(knowledge, AsteroidSizeClass.LARGE);
        AsteroidFieldNode medium = node(knowledge, AsteroidSizeClass.MEDIUM);
        AsteroidFieldNode small = node(knowledge, AsteroidSizeClass.SMALL);

        assertEquals(List.of(large.id()), visibleIds(knowledge));

        knowledge.detect(medium.id());

        assertEquals(List.of(large.id(), medium.id()), visibleIds(knowledge));

        knowledge.detect(small.id());

        assertEquals(List.of(large.id(), medium.id(), small.id()), visibleIds(knowledge));
    }

    @Test
    void oreDetailsFollowTeamKnowledgeLevel() {
        AsteroidFieldKnowledge knowledge = AsteroidFieldKnowledge.fromSnapshot(
            CelestialObjectId.FROZEN_BELT,
            profile(),
            new AsteroidFieldKnowledgeSnapshot(
                CelestialObjectId.FROZEN_BELT,
                List.of(
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        0,
                        AsteroidDetectionState.DETECTED,
                        AsteroidOreKnowledgeState.UNKNOWN),
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        1,
                        AsteroidDetectionState.DETECTED,
                        AsteroidOreKnowledgeState.SIGNATURE),
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        2,
                        AsteroidDetectionState.DETECTED,
                        AsteroidOreKnowledgeState.PROFILE))));

        List<AsteroidFieldStarmapEntry> entries = AsteroidFieldStarmapView.visibleEntries(knowledge);

        assertEquals(
            Optional.empty(),
            entries.get(0)
                .visibleOreProfileId());
        assertEquals(
            List.of(),
            entries.get(0)
                .visibleGtOreVeinIds());
        assertEquals(
            Optional.of("volatile_ice"),
            entries.get(1)
                .visibleOreProfileId());
        assertEquals(
            List.of(),
            entries.get(1)
                .visibleGtOreVeinIds());
        assertEquals(
            Optional.of("volatile_ice"),
            entries.get(2)
                .visibleOreProfileId());
        assertEquals(
            List.of("ice", "sulfur"),
            entries.get(2)
                .visibleGtOreVeinIds());
    }

    private static List<MinorCelestialBodyId> visibleIds(AsteroidFieldKnowledge knowledge) {
        return AsteroidFieldStarmapView.visibleEntries(knowledge)
            .stream()
            .map(AsteroidFieldStarmapEntry::id)
            .toList();
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
