package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Objects;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.core.Galaxia;

public record PlanetaryFeatureKey(ResourceLocation id) {

    public PlanetaryFeatureKey {
        Objects.requireNonNull(id, "Planetary feature id must not be null");
    }

    public static PlanetaryFeatureKey of(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Planetary feature path must not be null or blank");
        }
        return new PlanetaryFeatureKey(new ResourceLocation(Galaxia.MODID, path));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
