package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidFieldResolverTest {

    @Test
    void sameBeltAndProfileResolveIdenticalNodes() {
        AsteroidFieldProfile profile = profile(1);

        List<AsteroidFieldNode> first = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);
        List<AsteroidFieldNode> second = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);

        assertEquals(first, second);
        assertEquals(6, first.size());
        assertEquals(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN),
            first.get(0)
                .id());
    }

    @Test
    void generatedAsteroidsStartAfterReservedAuthoredSlots() {
        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile(1));

        assertEquals(
            AsteroidSlotRanges.GENERATED_SLOT_MIN,
            nodes.get(0)
                .index());
        assertTrue(
            nodes.stream()
                .allMatch(node -> AsteroidSlotRanges.isGeneratedSlot(node.index())));
    }

    @Test
    void generationVersionChangesGeneratedFacts() {
        List<AsteroidFieldNode> first = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile(1));
        List<AsteroidFieldNode> second = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile(2));

        assertNotEquals(first, second);
    }

    @Test
    void resolverSatisfiesSizeCountsAndPlacementRanges() {
        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile(1));

        assertEquals(
            1,
            nodes.stream()
                .filter(node -> node.sizeClass() == AsteroidSizeClass.LARGE)
                .count());
        assertEquals(
            2,
            nodes.stream()
                .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
                .count());
        assertEquals(
            3,
            nodes.stream()
                .filter(node -> node.sizeClass() == AsteroidSizeClass.SMALL)
                .count());
        for (AsteroidFieldNode node : nodes) {
            assertEquals(CelestialObjectId.FROZEN_BELT, node.beltId());
            assertTrue(node.angleOffsetDeg() >= 0.0 && node.angleOffsetDeg() < 360.0);
            assertTrue(node.orbitalDepth01() >= 0.0 && node.orbitalDepth01() <= 1.0);
            assertFalse(
                node.displayName()
                    .isBlank());
        }
    }

    @Test
    void initialDiscoveryStatesFollowSizeClassRules() {
        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile(1));
        AsteroidFieldNode large = nodes.stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.LARGE)
            .findFirst()
            .orElseThrow();
        AsteroidFieldNode medium = nodes.stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM)
            .findFirst()
            .orElseThrow();
        AsteroidFieldNode small = nodes.stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.SMALL)
            .findFirst()
            .orElseThrow();

        assertEquals(AsteroidDetectionState.DETECTED, AsteroidFieldResolver.initialDetectionState(large));
        assertEquals(AsteroidDetectionState.HIDDEN, AsteroidFieldResolver.initialDetectionState(medium));
        assertEquals(AsteroidDetectionState.HIDDEN, AsteroidFieldResolver.initialDetectionState(small));
        assertTrue(
            List.of(
                AsteroidOreKnowledgeState.UNKNOWN,
                AsteroidOreKnowledgeState.SIGNATURE,
                AsteroidOreKnowledgeState.PROFILE)
                .contains(AsteroidFieldResolver.initialOreKnowledge(large)));
        assertEquals(AsteroidOreKnowledgeState.UNKNOWN, AsteroidFieldResolver.initialOreKnowledge(medium));
        assertEquals(AsteroidOreKnowledgeState.UNKNOWN, AsteroidFieldResolver.initialOreKnowledge(small));
        assertTrue(
            List.of(
                AsteroidOreKnowledgeState.UNKNOWN,
                AsteroidOreKnowledgeState.SIGNATURE,
                AsteroidOreKnowledgeState.PROFILE)
                .contains(AsteroidFieldResolver.oreKnowledgeAfterDetection(medium)));
        assertEquals(AsteroidOreKnowledgeState.UNKNOWN, AsteroidFieldResolver.oreKnowledgeAfterDetection(small));
    }

    @Test
    void nodePresetOverridesGeneratedNameKindAndInitialVisibility() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(0, 1, 1)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", 2.0, List.of("galaxia:iron")))
            .nodePreset(1, AsteroidNodeKind.UNIQUE, "The Anvil", AsteroidDetectionState.DETECTED)
            .build();

        AsteroidFieldNode node = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 1);

        assertEquals(AsteroidNodeKind.UNIQUE, node.kind());
        assertEquals("The Anvil", node.displayName());
        assertEquals(AsteroidDetectionState.DETECTED, AsteroidFieldResolver.initialDetectionState(node));
    }

    private static AsteroidFieldProfile profile(int generationVersion) {
        return AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(generationVersion)
            .sizeCounts(1, 2, 3)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", 2.0, List.of("galaxia:iron")))
            .oreProfile(new AsteroidOreProfile("icy", 1.0, List.of("galaxia:ice")))
            .build();
    }
}
