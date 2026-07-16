package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Optional;

import javax.annotation.Nonnull;

/** Resolves runtime celestial bodies that are not part of the authored registry. */
public interface DynamicCelestialObjectProvider {

    Optional<CelestialObject> resolve(@Nonnull CelestialObjectKey key);
}
