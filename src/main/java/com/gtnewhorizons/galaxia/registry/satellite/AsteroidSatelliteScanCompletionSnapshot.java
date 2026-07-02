package com.gtnewhorizons.galaxia.registry.satellite;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public record AsteroidSatelliteScanCompletionSnapshot(@Nonnull CelestialObjectId beltId,
    @Nonnull MinorCelestialBodyId anchorAsteroidId, int generationVersion) {

    public AsteroidSatelliteScanCompletionSnapshot {
        if (!anchorAsteroidId.parentBeltId()
            .equals(beltId)) {
            throw new IllegalArgumentException("anchor asteroid parent belt must match completion belt");
        }
        if (generationVersion <= 0) throw new IllegalArgumentException("generationVersion must be positive");
    }
}
