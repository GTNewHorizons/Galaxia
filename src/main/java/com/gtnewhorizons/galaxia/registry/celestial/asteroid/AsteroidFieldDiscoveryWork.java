package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;

/**
 * Asteroid implementation of a discoverable fact: existence, ore signature, or
 * full ore profile for one minor body.
 */
public record AsteroidFieldDiscoveryWork(@Nonnull CelestialObjectKey targetKey, @Nonnull CelestialDiscoveryStep step)
    implements CelestialDiscoveryWork {

    public AsteroidFieldDiscoveryWork {
        if (!targetKey.isMinorBody()) {
            throw new IllegalArgumentException("Asteroid discovery work must target a minor body");
        }
    }

    static AsteroidFieldDiscoveryWork from(@Nonnull CelestialDiscoveryStep step, @Nonnull AsteroidFieldNode node) {
        return new AsteroidFieldDiscoveryWork(CelestialObjectKey.minorBody(node.id()), step);
    }

    public MinorCelestialBodyId asteroidId() {
        return targetKey.minorBodyId();
    }
}
