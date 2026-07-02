package com.gtnewhorizons.galaxia.registry.satellite;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public record AsteroidSatelliteScanSnapshot(@Nonnull CelestialAsset.ID satelliteId, @Nonnull CelestialObjectId beltId,
    @Nonnull MinorCelestialBodyId asteroidId, @Nonnull AsteroidSatelliteScanPass pass, int elapsedTicks) {

    public AsteroidSatelliteScanSnapshot {
        if (!asteroidId.parentBeltId()
            .equals(beltId)) {
            throw new IllegalArgumentException("asteroid parent belt must match scan belt");
        }
        if (elapsedTicks < 0 || elapsedTicks >= pass.durationTicks()) {
            throw new IllegalArgumentException("elapsedTicks must be in [0, pass duration)");
        }
    }
}
