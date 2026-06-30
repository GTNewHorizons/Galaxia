package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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

    @Test
    void generatedHiddenAsteroidsAreReachableFromDetectedAsteroids() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(1, 4, 6)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(75.0)
            .oreProfile(new AsteroidOreProfile("metallic", 2.0, List.of("galaxia:iron")))
            .build();

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);

        assertEveryHiddenNodeReachableFromDetectedNode(profile, nodes);
    }

    @Test
    void generatedHiddenAsteroidsCanChainBeyondInitialDetectedAnchors() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(0, 12, 0)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(75.0)
            .oreProfile(new AsteroidOreProfile("metallic", 2.0, List.of("galaxia:iron")))
            .nodePreset(
                new AsteroidNodePreset(
                    0,
                    AsteroidNodeKind.LORE,
                    "detected_anchor",
                    "Detected Anchor",
                    AsteroidSizeClass.LARGE,
                    AsteroidDetectionState.DETECTED,
                    AsteroidOreKnowledgeState.PROFILE,
                    0.0,
                    0.5,
                    null,
                    null))
            .build();

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldNode detectedAnchor = nodes.stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.DETECTED)
            .findFirst()
            .orElseThrow();

        assertEveryHiddenNodeReachableFromDetectedNode(profile, nodes);
        assertTrue(
            maxScanDepthFromDetectedNodes(profile, nodes) >= 4,
            "hidden asteroids should form scan chains instead of only clustering around detected anchors");
        assertTrue(
            nodes.stream()
                .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.HIDDEN)
                .anyMatch(node -> distance(profile, detectedAnchor, node) > profile.satelliteScanRadius()));
    }

    @Test
    void authoredHiddenAsteroidOutsideScanGraphFailsLoudly() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(99L)
            .generationVersion(1)
            .sizeCounts(1, 0, 0)
            .radialBand(1000.0, 2000.0)
            .satelliteScanRadius(50.0)
            .oreProfile(new AsteroidOreProfile("metallic", 2.0, List.of("galaxia:iron")))
            .nodePreset(
                new AsteroidNodePreset(
                    1,
                    AsteroidNodeKind.UNIQUE,
                    "isolated_hidden",
                    "Isolated Hidden",
                    AsteroidSizeClass.MEDIUM,
                    AsteroidDetectionState.HIDDEN,
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
            if (AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.DETECTED) {
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
                .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.HIDDEN)
                .allMatch(node -> visited.contains(node.id())));
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = AsteroidFieldOrbitModel.resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = AsteroidFieldOrbitModel.resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int maxScanDepthFromDetectedNodes(AsteroidFieldProfile profile, List<AsteroidFieldNode> nodes) {
        Map<MinorCelestialBodyId, Integer> depths = new HashMap<>();
        Queue<AsteroidFieldNode> queue = new ArrayDeque<>();
        for (AsteroidFieldNode node : nodes) {
            if (AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.DETECTED) {
                depths.put(node.id(), 0);
                queue.add(node);
            }
        }

        while (!queue.isEmpty()) {
            AsteroidFieldNode current = queue.remove();
            int nextDepth = depths.get(current.id()) + 1;
            for (AsteroidFieldNode candidate : nodes) {
                if (!depths.containsKey(candidate.id())
                    && distance(profile, current, candidate) <= profile.satelliteScanRadius()) {
                    depths.put(candidate.id(), nextDepth);
                    queue.add(candidate);
                }
            }
        }

        return depths.values()
            .stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
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
