package com.gtnewhorizons.galaxia.registry.orbital;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public interface MinorBodyOrbitResolver {

    @Nullable
    OrbitalMechanics.OrbitalState resolveWorldState(CelestialObject parent, CelestialObject child,
        OrbitalMechanics.OrbitalState parentState);
}
