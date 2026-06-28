package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public record CelestialBodyRef(CelestialObjectId registeredBodyId, MinorCelestialBodyId minorBodyId) {

    public CelestialBodyRef {
        if ((registeredBodyId == null) == (minorBodyId == null)) {
            throw new IllegalStateException("Celestial body ref must target exactly one body");
        }
    }

    public static CelestialBodyRef registered(CelestialObjectId id) {
        return new CelestialBodyRef(Objects.requireNonNull(id, "id cannot be null"), null);
    }

    public static CelestialBodyRef minorBody(MinorCelestialBodyId id) {
        return new CelestialBodyRef(null, Objects.requireNonNull(id, "id cannot be null"));
    }

    public boolean isRegistered() {
        return registeredBodyId != null;
    }

    public boolean isMinorBody() {
        return minorBodyId != null;
    }
}
