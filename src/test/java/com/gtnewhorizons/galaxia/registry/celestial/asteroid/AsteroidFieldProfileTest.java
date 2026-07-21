package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidFieldProfileTest {

    @Test
    void generatedAsteroidSizesAreInterleavedAcrossSlots() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(123L)
            .generationVersion(4)
            .sizeCounts(4, 8, 12)
            .radialBand(10.0, 20.0)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("galaxia:iron")))
            .build();
        List<AsteroidSizeClass> resolvedSizes = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .map(AsteroidFieldNode::sizeClass)
            .toList();
        List<AsteroidSizeClass> firstSlots = resolvedSizes.stream()
            .limit(8)
            .toList();

        assertNotEquals(
            List.of(
                AsteroidSizeClass.LARGE,
                AsteroidSizeClass.LARGE,
                AsteroidSizeClass.LARGE,
                AsteroidSizeClass.LARGE,
                AsteroidSizeClass.MEDIUM,
                AsteroidSizeClass.MEDIUM,
                AsteroidSizeClass.MEDIUM,
                AsteroidSizeClass.MEDIUM),
            firstSlots);
        assertTrue(
            firstSlots.stream()
                .distinct()
                .count() > 1);
        assertEquals(
            4,
            resolvedSizes.stream()
                .filter(size -> size == AsteroidSizeClass.LARGE)
                .count());
        assertEquals(
            8,
            resolvedSizes.stream()
                .filter(size -> size == AsteroidSizeClass.MEDIUM)
                .count());
        assertEquals(
            12,
            resolvedSizes.stream()
                .filter(size -> size == AsteroidSizeClass.SMALL)
                .count());
    }

    @Test
    void profileDefinesCountsBandAndOrePool() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", List.of("galaxia:iron"));

        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(123L)
            .generationVersion(4)
            .sizeCounts(1, 2, 3)
            .radialBand(10.0, 20.0)
            .placementConnectionRadius(1000.0)
            .oreProfile(metallic)
            .build();

        assertEquals(123L, profile.seedSalt());
        assertEquals(4, profile.generationVersion());
        assertEquals(6, profile.totalNodes());
        assertEquals(1, profile.largeCount());
        assertEquals(2, profile.mediumCount());
        assertEquals(3, profile.smallCount());
        assertEquals(10.0, profile.innerOrbitalRadius());
        assertEquals(20.0, profile.outerOrbitalRadius());
        assertEquals(1000.0, profile.placementConnectionRadius());
        assertEquals(List.of(metallic), profile.oreProfiles());
    }

    @Test
    void weightedOreProfilesSelectByRoll() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", List.of("ore.mix.iron"));
        AsteroidOreProfile volatileIce = new AsteroidOreProfile("volatile_ice", List.of("ore.mix.lapis"));
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(1.0, 2.0)
            .placementConnectionRadius(1000.0)
            .oreProfile(metallic, 3.0)
            .oreProfile(volatileIce, 1.0)
            .build();

        assertEquals(List.of(metallic, volatileIce), profile.oreProfiles());
        assertEquals(metallic, profile.selectOreProfile(0.0));
        assertEquals(metallic, profile.selectOreProfile(0.74));
        assertEquals(volatileIce, profile.selectOreProfile(0.75));
        assertEquals(volatileIce, profile.selectOreProfile(0.99));
        assertEquals(volatileIce, profile.requireOreProfile("volatile_ice"));
    }

    @Test
    void profileRejectsInvalidAuthoring() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", List.of("galaxia:iron"));

        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .sizeCounts(-1, 0, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .generationVersion(0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .radialBand(0.0, 1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .radialBand(2.0, 1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .placementConnectionRadius(-1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldProfile.builder()
                .oreProfile(null));
        assertThrows(
            IllegalStateException.class,
            () -> AsteroidFieldProfile.builder()
                .sizeCounts(0, 0, 0)
                .radialBand(1.0, 2.0)
                .placementConnectionRadius(1000.0)
                .oreProfile(metallic)
                .build());
        assertThrows(
            IllegalStateException.class,
            () -> AsteroidFieldProfile.builder()
                .sizeCounts(1, 0, 0)
                .radialBand(1.0, 2.0)
                .placementConnectionRadius(1000.0)
                .build());
    }
}
