package com.gtnewhorizons.galaxia.registry.satellite;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;

public record AsteroidSatelliteScanSnapshot(@Nonnull CelestialObjectId beltId,
    @Nonnull MinorCelestialBodyId anchorAsteroidId, @Nonnull MinorCelestialBodyId targetAsteroidId,
    @Nonnull AsteroidFieldScanPass pass, int elapsedTicks, int workerCount) {

    public AsteroidSatelliteScanSnapshot {
        if (!anchorAsteroidId.parentBodyId()
            .equals(beltId)) {
            throw new IllegalArgumentException("anchor asteroid parent body must match scan belt");
        }
        if (!targetAsteroidId.parentBodyId()
            .equals(beltId)) {
            throw new IllegalArgumentException("target asteroid parent body must match scan belt");
        }
        if (elapsedTicks < 0 || elapsedTicks >= pass.durationTicks()) {
            throw new IllegalArgumentException("elapsedTicks must be in [0, pass duration)");
        }
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be positive");
    }
}
