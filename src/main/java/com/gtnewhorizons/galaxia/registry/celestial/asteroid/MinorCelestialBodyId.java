package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record MinorCelestialBodyId(CelestialObjectId parentBeltId, int index) {

    public MinorCelestialBodyId {
        parentBeltId = Objects.requireNonNull(parentBeltId, "parentBeltId cannot be null");
        if (index < 0) {
            throw new IllegalArgumentException("minor body index must be non-negative");
        }
    }
}
