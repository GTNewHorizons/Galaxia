package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.DynamicCelestialObjectProvider;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryView;

public final class AsteroidDynamicCelestialObjectProvider implements DynamicCelestialObjectProvider {

    private final Function<CelestialObjectKey, Optional<CelestialObject>> registeredObjects;

    public AsteroidDynamicCelestialObjectProvider(
        @Nonnull Function<CelestialObjectKey, Optional<CelestialObject>> registeredObjects) {
        if (registeredObjects == null) throw new IllegalArgumentException("registeredObjects is required");
        this.registeredObjects = registeredObjects;
    }

    @Override
    public Optional<CelestialObject> resolve(@Nonnull CelestialObjectKey key) {
        if (key == null) return Optional.empty();
        return AsteroidCelestialMaterializer
            .resolveMinorBody(key, beltId -> registeredObjects.apply(CelestialObjectKey.registered(beltId)));
    }

    @Override
    public List<CelestialObject> dynamicChildren(@Nonnull CelestialObjectKey parentId,
        @Nonnull CelestialDiscoveryView discoveryView, boolean includeHidden) {
        return AsteroidCelestialMaterializer.knownChildren(
            parentId,
            discoveryView,
            includeHidden,
            beltId -> registeredObjects.apply(CelestialObjectKey.registered(beltId)));
    }

}
