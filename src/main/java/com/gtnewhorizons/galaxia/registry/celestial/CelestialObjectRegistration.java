package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalParams;

@Desugar
public record CelestialObjectRegistration(CelestialObjectId id, String name, String nameKey, CelestialObjectId parentId,
    DimensionEnum dimensionEnum, CelestialObjectClass objectClass, OrbitalParams orbitalParams,
    AbsolutePosition absolutePosition, ResourceLocation texture, double spriteSize,
    CelestialBodyProperties properties) {

    public CelestialObjectRegistration {
        if (id == null) throw new IllegalStateException("Celestial object id is required");
        if (name == null || name.isEmpty()) throw new IllegalStateException("Celestial object name is required");
        objectClass = objectClass == null ? CelestialObjectClass.PLANET : objectClass;
        orbitalParams = orbitalParams == null ? OrbitalParams.circular(0.0, 0.0) : orbitalParams;
        texture = texture;
        spriteSize = spriteSize;
        properties = properties == null ? CelestialBodyProperties.builder()
            .build() : properties;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {

        private CelestialObjectId id;
        private String name;
        private String nameKey;
        private CelestialObjectId parentId;
        private DimensionEnum dimensionEnum;
        private CelestialObjectClass objectClass = CelestialObjectClass.PLANET;
        private OrbitalParams orbitalParams = OrbitalParams.circular(0.0, 0.0);
        private AbsolutePosition absolutePosition;
        private ResourceLocation texture;
        private double spriteSize;
        private CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .build();

        public Builder() {}

        public Builder(CelestialObjectRegistration source) {
            if (source == null) return;
            this.id = source.id;
            this.name = source.name;
            this.nameKey = source.nameKey;
            this.parentId = source.parentId;
            this.dimensionEnum = source.dimensionEnum;
            this.objectClass = source.objectClass;
            this.orbitalParams = source.orbitalParams;
            this.absolutePosition = source.absolutePosition;
            this.texture = source.texture;
            this.spriteSize = source.spriteSize;
            this.properties = source.properties;
        }

        public Builder id(CelestialObjectId value) {
            this.id = value;
            this.name = value.displayName();
            return this;
        }

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder nameKey(String value) {
            this.nameKey = value;
            return this;
        }

        public Builder parent(CelestialObjectId value) {
            this.parentId = value;
            return this;
        }

        public Builder parentId(CelestialObjectId value) {
            this.parentId = value;
            return this;
        }

        public Builder dimension(DimensionEnum value) {
            this.dimensionEnum = value;
            if (value != null) {
                if (this.id == null) this.id = CelestialObjectId.fromDimension(value);
                if (this.name == null) this.name = value.getName();
                if (this.nameKey == null) this.nameKey = value.getTranslationKey();
            }
            return this;
        }

        public Builder dimensionEnum(DimensionEnum value) {
            this.dimensionEnum = value;
            return this;
        }

        public Builder objectClass(CelestialObjectClass value) {
            this.objectClass = value == null ? CelestialObjectClass.PLANET : value;
            return this;
        }

        public Builder orbitalParams(OrbitalParams value) {
            this.orbitalParams = Objects.requireNonNull(value, "orbitalParams");
            return this;
        }

        public Builder circularOrbit(double radius, double orbitSpeed) {
            this.orbitalParams = OrbitalParams.circular(radius, orbitSpeed);
            return this;
        }

        public Builder circularOrbit(double radius, double orbitSpeed, double meanAnomalyAtEpoch) {
            this.orbitalParams = OrbitalParams.circular(radius, orbitSpeed, meanAnomalyAtEpoch);
            return this;
        }

        public Builder absolutePosition(double x, double y) {
            this.absolutePosition = new AbsolutePosition(x, y);
            return this;
        }

        public Builder absolutePosition(AbsolutePosition value) {
            this.absolutePosition = value;
            return this;
        }

        public Builder texture(ResourceLocation value) {
            this.texture = value;
            return this;
        }

        public Builder texture(String modid, String path) {
            this.texture = new ResourceLocation(modid, path);
            return this;
        }

        public Builder texture(String path) {
            this.texture = GalaxiaAPI.LocationGalaxia(path);
            return this;
        }

        public Builder spriteSize(double value) {
            this.spriteSize = value;
            return this;
        }

        public Builder properties(CelestialBodyProperties value) {
            this.properties = value == null ? CelestialBodyProperties.builder()
                .build() : value;
            return this;
        }

        public Builder properties(Consumer<CelestialBodyProperties.Builder> mutator) {
            CelestialBodyProperties.Builder builder = properties.toBuilder();
            mutator.accept(builder);
            this.properties = builder.build();
            return this;
        }

        public CelestialObjectRegistration build() {
            return new CelestialObjectRegistration(
                id,
                name,
                nameKey,
                parentId,
                dimensionEnum,
                objectClass,
                orbitalParams,
                absolutePosition,
                texture,
                spriteSize,
                properties);
        }
    }
}
