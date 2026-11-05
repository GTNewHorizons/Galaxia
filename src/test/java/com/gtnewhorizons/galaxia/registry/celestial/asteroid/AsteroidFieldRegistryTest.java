package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidFieldRegistryTest {

    @Test
    void frozenBeltRegistersAsteroidFieldProfile() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();

        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        assertNotNull(profile);

        List<AsteroidFieldNode> nodes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);

        assertTrue(
            nodes.stream()
                .anyMatch(node -> node.sizeClass() == AsteroidSizeClass.LARGE));
        assertTrue(
            nodes.stream()
                .anyMatch(node -> node.sizeClass() == AsteroidSizeClass.MEDIUM));
        assertTrue(
            nodes.stream()
                .anyMatch(node -> node.sizeClass() == AsteroidSizeClass.SMALL));
    }
}
