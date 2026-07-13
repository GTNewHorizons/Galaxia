package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public final class AsteroidStarmapPresentationPolicy {

    private static final float MAP_ICON_BASE_SCALE = 60f;
    private static final float MIN_RENDERED_DIAMETER = 2f;

    private AsteroidStarmapPresentationPolicy() {}

    public static float spriteRadius(@Nullable CelestialObject body, float spriteSize, double relativeZoom) {
        if (!isAsteroid(body) || spriteSize <= 0.0001f) return 0f;
        return Math.max(0.0f, spriteSize * MAP_ICON_BASE_SCALE * (float) relativeZoom);
    }

    public static boolean shouldCull(@Nullable CelestialObject body, @Nonnull AsteroidStarmapProjection projection,
        float naturalRadius) {
        return isAsteroid(body) && projection.shouldCullAtNaturalRadius(naturalRadius, MIN_RENDERED_DIAMETER);
    }

    private static boolean isAsteroid(@Nullable CelestialObject body) {
        return body != null && body.objectClass() == CelestialObject.Class.ASTEROID;
    }
}
