package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

final class AsteroidFieldProfileTest {

    @Test
    void profileDefinesCountsBandAndOrePool() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", 2.0, List.of("galaxia:iron"));

        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .seedSalt(123L)
            .generationVersion(4)
            .sizeCounts(1, 2, 3)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
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
        assertEquals(1000.0, profile.satelliteScanRadius());
        assertEquals(List.of(metallic), profile.oreProfiles());
    }

    @Test
    void profileRejectsInvalidAuthoring() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", 1.0, List.of("galaxia:iron"));

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
                .satelliteScanRadius(-1.0));
        assertThrows(
            NullPointerException.class,
            () -> AsteroidFieldProfile.builder()
                .oreProfile(null));
        assertThrows(
            IllegalStateException.class,
            () -> AsteroidFieldProfile.builder()
                .sizeCounts(0, 0, 0)
                .radialBand(1.0, 2.0)
                .satelliteScanRadius(1000.0)
                .oreProfile(metallic)
                .build());
        assertThrows(
            IllegalStateException.class,
            () -> AsteroidFieldProfile.builder()
                .sizeCounts(1, 0, 0)
                .radialBand(1.0, 2.0)
                .satelliteScanRadius(1000.0)
                .build());
    }
}
