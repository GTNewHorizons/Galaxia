package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

final class AsteroidOreProfilePoolTest {

    @Test
    void weightedPoolSelectsReusableProfilesByRoll() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", List.of("ore.mix.iron"));
        AsteroidOreProfile volatileIce = new AsteroidOreProfile("volatile_ice", List.of("ore.mix.lapis"));
        AsteroidOreProfilePool pool = AsteroidOreProfilePool.builder()
            .profile(metallic, 3.0)
            .profile(volatileIce, 1.0)
            .build();

        assertEquals(List.of(metallic, volatileIce), pool.profiles());
        assertEquals(metallic, pool.select(0.0));
        assertEquals(metallic, pool.select(0.74));
        assertEquals(volatileIce, pool.select(0.75));
        assertEquals(volatileIce, pool.select(0.99));
        assertEquals(volatileIce, pool.requireProfile("volatile_ice"));
    }

    @Test
    void weightedPoolRejectsMissingProfilesAndInvalidWeights() {
        AsteroidOreProfile metallic = new AsteroidOreProfile("metallic", List.of("ore.mix.iron"));

        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidOreProfilePool.builder()
                .profile(metallic, 0.0));
        assertThrows(
            IllegalStateException.class,
            () -> AsteroidOreProfilePool.builder()
                .build());
        assertThrows(
            IllegalStateException.class,
            () -> AsteroidOreProfilePool.builder()
                .profile(metallic, 1.0)
                .build()
                .requireProfile("missing"));
    }
}
