package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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
