package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public record AsteroidSatelliteScanCompletionSnapshot(CelestialObjectId beltId, MinorCelestialBodyId anchorAsteroidId,
    int generationVersion) {

    public AsteroidSatelliteScanCompletionSnapshot {
        beltId = Objects.requireNonNull(beltId, "beltId cannot be null");
        anchorAsteroidId = Objects.requireNonNull(anchorAsteroidId, "anchorAsteroidId cannot be null");
        if (!anchorAsteroidId.parentBeltId()
            .equals(beltId)) {
            throw new IllegalArgumentException("anchor asteroid parent belt must match completion belt");
        }
        if (generationVersion <= 0) throw new IllegalArgumentException("generationVersion must be positive");
    }
}
