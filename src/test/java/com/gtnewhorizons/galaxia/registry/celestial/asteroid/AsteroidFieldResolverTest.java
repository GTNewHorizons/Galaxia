package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

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

        assertEquals(DiscoveryState.DISCOVERED, AsteroidFieldResolver.initialDetectionState(large));
        assertEquals(DiscoveryState.HIDDEN, AsteroidFieldResolver.initialDetectionState(medium));
        assertEquals(DiscoveryState.HIDDEN, AsteroidFieldResolver.initialDetectionState(small));
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
    void authoredAsteroidOverridesGeneratedNameKindAndInitialVisibility() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(0, 1, 1)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .authoredAsteroid(1, AsteroidNodeKind.UNIQUE, "The Anvil", DiscoveryState.DISCOVERED)
            .build();

        AsteroidFieldNode node = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 1);

        assertEquals(AsteroidNodeKind.UNIQUE, node.kind());
        assertEquals("The Anvil", node.displayName());
        assertEquals(DiscoveryState.DISCOVERED, AsteroidFieldResolver.initialDetectionState(node));
    }

    @Test
    void generatedHiddenAsteroidsAreReachableFromDetectedAsteroids() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(1, 4, 6)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(75.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .build();

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);

        assertEveryHiddenNodeReachableFromDetectedNode(profile, nodes);
    }

    @Test
    void generatedHiddenAsteroidsAvoidSingleLineAndOvercrowdedClusters() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(0, 24, 0)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(75.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .authoredAsteroid(
                new AuthoredAsteroidDefinition(
                    0,
                    AsteroidNodeKind.LORE,
                    "detected_anchor",
                    "Detected Anchor",
                    AsteroidSizeClass.LARGE,
                    DiscoveryState.DISCOVERED,
                    AsteroidOreKnowledgeState.PROFILE,
                    0.0,
                    0.5,
                    null,
                    null))
            .build();

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);

        assertEveryHiddenNodeReachableFromDetectedNode(profile, nodes);
        assertTrue(
            maxHiddenNeighborsWithinScanRadius(profile, nodes) >= 3,
            "hidden asteroid placement should branch instead of collapsing into one chain");
        assertTrue(
            maxHiddenNeighborsWithinScanRadius(profile, nodes) <= 8,
            "hidden asteroid placement should discourage overcrowded scan neighborhoods");
    }

    @Test
    void authoredHiddenAsteroidOutsideScanGraphFailsLoudly() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(50.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .authoredAsteroid(
                new AuthoredAsteroidDefinition(
                    1,
                    AsteroidNodeKind.UNIQUE,
                    "isolated_hidden",
                    "Isolated Hidden",
                    AsteroidSizeClass.MEDIUM,
                    DiscoveryState.HIDDEN,
                    null,
                    180.0,
                    1.0,
                    null,
                    null))
            .build();

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile));

        assertTrue(
            error.getMessage()
                .contains("unreachable hidden asteroid"));
    }

    private static void assertEveryHiddenNodeReachableFromDetectedNode(AsteroidFieldProfile profile,
        List<AsteroidFieldNode> nodes) {
        Set<MinorCelestialBodyId> visited = new HashSet<>();
        Queue<AsteroidFieldNode> queue = new ArrayDeque<>();
        for (AsteroidFieldNode node : nodes) {
            if (AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.DISCOVERED) {
                visited.add(node.id());
                queue.add(node);
            }
        }

        while (!queue.isEmpty()) {
            AsteroidFieldNode current = queue.remove();
            for (AsteroidFieldNode candidate : nodes) {
                if (!visited.contains(candidate.id())
                    && distance(profile, current, candidate) <= profile.satelliteScanRadius()) {
                    visited.add(candidate.id());
                    queue.add(candidate);
                }
            }
        }

        assertTrue(
            nodes.stream()
                .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.HIDDEN)
                .allMatch(node -> visited.contains(node.id())));
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int maxHiddenNeighborsWithinScanRadius(AsteroidFieldProfile profile, List<AsteroidFieldNode> nodes) {
        int maxNeighbors = 0;
        for (AsteroidFieldNode node : nodes) {
            if (AsteroidFieldResolver.initialDetectionState(node) != DiscoveryState.HIDDEN) continue;
            int neighbors = 0;
            for (AsteroidFieldNode candidate : nodes) {
                if (candidate != node && AsteroidFieldResolver.initialDetectionState(candidate) == DiscoveryState.HIDDEN
                    && distance(profile, node, candidate) <= profile.satelliteScanRadius()) {
                    neighbors++;
                }
            }
            maxNeighbors = Math.max(maxNeighbors, neighbors);
        }
        return maxNeighbors;
    }

    private static AsteroidFieldProfile profile(int generationVersion) {
        return AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(generationVersion)
            .sizeCounts(1, 2, 3)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .oreProfile(new AsteroidOreProfile("icy", List.of("galaxia:ice")))
            .build();
    }
}
