package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record MinorCelestialBodyId(@Nonnull CelestialObjectId parentBeltId, int index) {

    public MinorCelestialBodyId {
        if (index < 0) {
            throw new IllegalArgumentException("minor body index must be non-negative");
        }
    }
}
