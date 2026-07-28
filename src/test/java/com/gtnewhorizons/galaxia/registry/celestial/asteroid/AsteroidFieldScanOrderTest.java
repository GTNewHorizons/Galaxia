package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidFieldScanOrderTest {

    @Test
    void discoveryOrderMapsAsteroidSizeToGenericImportance() {
        AsteroidFieldNode large = node(1, AsteroidSizeClass.LARGE, 0.9);
        AsteroidFieldNode medium = node(2, AsteroidSizeClass.MEDIUM, 0.1);
        AsteroidFieldNode small = node(3, AsteroidSizeClass.SMALL, 0.0);
        List<AsteroidFieldNode> nodes = new ArrayList<>(List.of(small, medium, large));

        nodes.sort(AsteroidFieldScanOrder.discoveryOrder());

        assertEquals(List.of(large, medium, small), nodes);
    }

    @Test
    void discoveryOrderKeepsNearestAsteroidsFirstWithinSameSize() {
        AsteroidFieldNode outer = node(1, AsteroidSizeClass.MEDIUM, 0.9);
        AsteroidFieldNode inner = node(2, AsteroidSizeClass.MEDIUM, 0.1);
        AsteroidFieldNode middle = node(3, AsteroidSizeClass.MEDIUM, 0.5);
        List<AsteroidFieldNode> nodes = new ArrayList<>(List.of(outer, middle, inner));

        nodes.sort(AsteroidFieldScanOrder.discoveryOrder());

        assertEquals(List.of(inner, middle, outer), nodes);
    }

    private static AsteroidFieldNode node(int index, AsteroidSizeClass sizeClass, double orbitalDepth01) {
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, index),
            CelestialObjectId.FROZEN_BELT,
            index,
            "Test " + index,
            AsteroidNodeKind.GENERATED,
            sizeClass,
            DiscoveryState.HIDDEN,
            0.0,
            orbitalDepth01,
            new AsteroidOreProfile("test", List.of("test_vein")),
            new AsteroidAppearanceProfile("test_icon", index));
    }
}
