package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Objects;

import net.minecraft.util.ResourceLocation;

public record PlanetaryFeatureDefinition(PlanetaryFeatureKey key, String displayName, ResourceLocation texture,
    String description, PlanetaryFeatureLayer layer, PlanetaryFeaturePlacement placement) {

    public PlanetaryFeatureDefinition {
        Objects.requireNonNull(key, "Planetary feature key must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Planetary feature displayName must not be null or blank");
        }
        Objects.requireNonNull(texture, "Planetary feature texture must not be null");
        description = description == null ? "" : description;
        Objects.requireNonNull(layer, "Planetary feature layer must not be null");
        Objects.requireNonNull(placement, "Planetary feature placement must not be null");
    }

    public static Builder builder(PlanetaryFeatureKey key) {
        return new Builder(key);
    }

    public static Builder builder(String path) {
        return builder(PlanetaryFeatureKey.of(path));
    }

    public static final class Builder {

        private final PlanetaryFeatureKey key;
        private String displayName;
        private ResourceLocation texture;
        private String description = "";
        private PlanetaryFeatureLayer layer = PlanetaryFeatureLayer.RESOURCE;
        private PlanetaryFeaturePlacement placement = PlanetaryFeaturePlacement.patch(12.0, 4.0);

        private Builder(PlanetaryFeatureKey key) {
            this.key = Objects.requireNonNull(key, "Planetary feature key must not be null");
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder layer(PlanetaryFeatureLayer layer) {
            this.layer = layer;
            return this;
        }

        public Builder placement(PlanetaryFeaturePlacement placement) {
            this.placement = placement;
            return this;
        }

        public PlanetaryFeatureDefinition build() {
            return new PlanetaryFeatureDefinition(key, displayName, texture, description, layer, placement);
        }
    }
}
