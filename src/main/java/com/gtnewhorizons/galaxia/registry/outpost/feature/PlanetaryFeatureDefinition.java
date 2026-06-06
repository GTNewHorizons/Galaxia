package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Objects;

import net.minecraft.util.ResourceLocation;

import lombok.Builder;

@Builder
public record PlanetaryFeatureDefinition(PlanetaryFeatureKey key, String displayName, ResourceLocation texture,
    String description, PlanetaryFeatureLayer layer, PlanetaryFeaturePlacement placement) {

    public PlanetaryFeatureDefinition {
        Objects.requireNonNull(key, "Planetary feature key must not be null");

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Planetary feature displayName must not be null or blank");
        }

        Objects.requireNonNull(texture, "Planetary feature texture must not be null");

        description = description == null ? "" : description;

        layer = layer == null ? PlanetaryFeatureLayer.RESOURCE : layer;

        placement = placement == null ? PlanetaryFeaturePlacement.patch(12.0, 4.0) : placement;
    }

    public static PlanetaryFeatureDefinitionBuilder builder(PlanetaryFeatureKey key) {
        return new PlanetaryFeatureDefinitionBuilder().key(key);
    }

    public static PlanetaryFeatureDefinitionBuilder builder(String path) {
        return builder(PlanetaryFeatureKey.of(path));
    }
}
