package com.gtnewhorizons.galaxia.registry.celestial;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record MinorCelestialBodyId(@Nonnull CelestialObjectId parentBodyId, int index) {

    public MinorCelestialBodyId {
        if (parentBodyId == null) {
            throw new IllegalArgumentException("parentBodyId cannot be null");
        }
        if (index < 0) {
            throw new IllegalArgumentException("minor body index must be non-negative");
        }
    }
}
