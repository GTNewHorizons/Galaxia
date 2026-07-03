package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public interface CelestialDiscoveryView {

    Optional<DiscoveryState> discoveryState(@Nonnull CelestialObjectKey key);
}
