package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/**
 * Stable identity for anything that can own assets or appear as a starmap body.
 *
 * Registered bodies keep using the enum-backed id. Minor bodies are derived from
 * a registered parent container plus a stable slot index, so generated asteroids
 * can be addressed without adding infinite enum values.
 */
public record CelestialObjectKey(CelestialObjectId registeredBodyId, MinorCelestialBodyId minorBodyId) {

    public CelestialObjectKey {
        if ((registeredBodyId == null) == (minorBodyId == null)) {
            throw new IllegalStateException("Celestial object key must target exactly one body");
        }
    }

    public static CelestialObjectKey registered(CelestialObjectId id) {
        return new CelestialObjectKey(Objects.requireNonNull(id, "id cannot be null"), null);
    }

    public static CelestialObjectKey minorBody(MinorCelestialBodyId id) {
        return new CelestialObjectKey(null, Objects.requireNonNull(id, "id cannot be null"));
    }

    public boolean isRegistered() {
        return registeredBodyId != null;
    }

    public boolean isMinorBody() {
        return minorBodyId != null;
    }

    public CelestialObjectId requireRegisteredBodyId() {
        if (registeredBodyId == null) {
            throw new IllegalStateException("Expected registered celestial object key");
        }
        return registeredBodyId;
    }
}
