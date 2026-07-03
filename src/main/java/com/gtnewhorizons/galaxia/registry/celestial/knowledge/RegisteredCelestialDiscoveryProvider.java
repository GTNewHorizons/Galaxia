package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

final class RegisteredCelestialDiscoveryProvider implements CelestialDiscoveryProvider {

    @Override
    public Optional<DiscoveryState> discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        if (!key.isRegistered()) return Optional.empty();
        if (CelestialRegistry.get(key)
            .isEmpty()) {
            throw new IllegalStateException("Unknown celestial object: " + key);
        }
        return Optional.of(DiscoveryState.DISCOVERED);
    }
}
