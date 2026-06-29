package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public record AsteroidSatelliteScanSnapshot(CelestialAsset.ID satelliteId, CelestialObjectId beltId,
    MinorCelestialBodyId asteroidId, AsteroidSatelliteScanPass pass, int elapsedTicks) {

    public AsteroidSatelliteScanSnapshot {
        satelliteId = Objects.requireNonNull(satelliteId, "satelliteId cannot be null");
        beltId = Objects.requireNonNull(beltId, "beltId cannot be null");
        asteroidId = Objects.requireNonNull(asteroidId, "asteroidId cannot be null");
        pass = Objects.requireNonNull(pass, "pass cannot be null");
        if (!asteroidId.parentBeltId()
            .equals(beltId)) {
            throw new IllegalArgumentException("asteroid parent belt must match scan belt");
        }
        if (elapsedTicks < 0 || elapsedTicks >= pass.durationTicks()) {
            throw new IllegalArgumentException("elapsedTicks must be in [0, pass duration)");
        }
    }
}
