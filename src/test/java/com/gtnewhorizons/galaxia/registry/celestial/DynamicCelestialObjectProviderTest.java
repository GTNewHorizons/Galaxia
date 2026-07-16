package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class DynamicCelestialObjectProviderTest {

    @Test
    void registeredProviderResolvesRuntimeMinorBodyThroughPublicRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        CelestialObjectKey key = CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.MARS, 91));
        CelestialObject body = CelestialObject.builder()
            .id(key)
            .name("Runtime body")
            .parent(CelestialObjectId.MARS)
            .objectClass(CelestialObject.Class.ASTEROID)
            .build();
        DynamicCelestialObjectProvider provider = new DynamicCelestialObjectProvider() {

            @Override
            public Optional<CelestialObject> resolve(CelestialObjectKey candidate) {
                return key.equals(candidate) ? Optional.of(body) : Optional.empty();
            }
        };

        assertSame(
            body,
            provider.resolve(key)
                .orElseThrow());
    }
}
