package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryView;

public interface DynamicCelestialObjectProvider {

    Optional<CelestialObject> resolve(@Nonnull CelestialObjectKey key);

    List<CelestialObject> children(@Nonnull CelestialObjectKey parentId, @Nonnull CelestialDiscoveryView discoveryView,
        boolean includeHidden);
}
