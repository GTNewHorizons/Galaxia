package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.function.Consumer;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalParams;

@Desugar
public record CelestialObjectRegistration(CelestialObjectIdentity identity, CelestialOrbitDefinition orbit,
    CelestialVisualProfile visuals, CelestialBodyProperties properties) {

    public CelestialObjectRegistration {
        orbit = orbit == null ? CelestialOrbitDefinition.stationary() : orbit;
        visuals = visuals == null ? CelestialVisualProfile.NONE : visuals;
        properties = properties == null ? CelestialBodyProperties.builder()
            .build() : properties;
    }

    public String id() {
        return identity.id();
    }

    public String name() {
        return identity.name();
    }

    public String nameKey() {
        return identity.nameKey();
    }

    public String parentId() {
        return identity.parentId();
    }

    public DimensionEnum dimensionEnum() {
        return identity.dimensionEnum();
    }

    public CelestialObjectClass objectClass() {
        return identity.objectClass();
    }

    public OrbitalParams orbitalParams() {
        return orbit.orbitalParams();
    }

    public AbsolutePosition absolutePosition() {
        return orbit.absolutePosition();
    }

    public ResourceLocation texture() {
        return visuals.texture();
    }

    public double spriteSize() {
        return visuals.spriteSize();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {

        private CelestialObjectIdentity.Builder identity = CelestialObjectIdentity.builder();
        private CelestialOrbitDefinition.Builder orbit = CelestialOrbitDefinition.builder();
        private CelestialVisualProfile.Builder visuals = CelestialVisualProfile.builder();
        private CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .build();

        public Builder() {}

        public Builder(CelestialObjectRegistration source) {
            if (source == null) return;
            this.identity = source.identity()
                .toBuilder();
            this.orbit = source.orbit()
                .toBuilder();
            this.visuals = source.visuals()
                .toBuilder();
            this.properties = source.properties();
        }

        public Builder identity(CelestialObjectIdentity value) {
            this.identity = value == null ? CelestialObjectIdentity.builder() : value.toBuilder();
            return this;
        }

        public Builder identity(Consumer<CelestialObjectIdentity.Builder> mutator) {
            mutator.accept(this.identity);
            return this;
        }

        public Builder id(String value) {
            this.identity.id(value);
            return this;
        }

        public Builder name(String value) {
            this.identity.name(value);
            return this;
        }

        public Builder nameKey(String value) {
            this.identity.nameKey(value);
            return this;
        }

        public Builder parent(String value) {
            this.identity.parentId(value);
            return this;
        }

        public Builder dimension(DimensionEnum value) {
            this.identity.dimensionEnum(value);
            return this;
        }

        public Builder objectClass(CelestialObjectClass value) {
            this.identity.objectClass(value);
            return this;
        }

        public Builder orbit(CelestialOrbitDefinition value) {
            this.orbit = value == null ? CelestialOrbitDefinition.builder() : value.toBuilder();
            return this;
        }

        public Builder orbit(Consumer<CelestialOrbitDefinition.Builder> mutator) {
            mutator.accept(this.orbit);
            return this;
        }

        public Builder orbital(OrbitalParams value) {
            this.orbit.orbitalParams(value);
            return this;
        }

        public Builder circularOrbit(double radius, double orbitSpeed) {
            this.orbit.circularOrbit(radius, orbitSpeed);
            return this;
        }

        public Builder circularOrbit(double radius, double orbitSpeed, double meanAnomalyAtEpoch) {
            this.orbit.circularOrbit(radius, orbitSpeed, meanAnomalyAtEpoch);
            return this;
        }

        public Builder absolutePosition(double x, double y) {
            this.orbit.absolutePosition(x, y);
            return this;
        }

        public Builder visuals(CelestialVisualProfile value) {
            this.visuals = value == null ? CelestialVisualProfile.builder() : value.toBuilder();
            return this;
        }

        public Builder visuals(Consumer<CelestialVisualProfile.Builder> mutator) {
            mutator.accept(this.visuals);
            return this;
        }

        public Builder texture(ResourceLocation value) {
            this.visuals.texture(value);
            return this;
        }

        public Builder texture(String modid, String path) {
            this.visuals.texture(modid, path);
            return this;
        }

        public Builder texture(String path) {
            this.visuals.texture(path);
            return this;
        }

        public Builder spriteSize(double value) {
            this.visuals.spriteSize(value);
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
            return new CelestialObjectRegistration(identity.build(), orbit.build(), visuals.build(), properties);
        }
    }
}

@Desugar
record CelestialObjectIdentity(String id, String name, String nameKey, String parentId, DimensionEnum dimensionEnum,
    CelestialObjectClass objectClass) {

    CelestialObjectIdentity {
        if (id == null || id.isEmpty()) throw new IllegalStateException("Celestial object id is required");
        if (name == null || name.isEmpty()) throw new IllegalStateException("Celestial object name is required");
        objectClass = objectClass == null ? CelestialObjectClass.PLANET : objectClass;
    }

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private String id;
        private String name;
        private String nameKey;
        private String parentId;
        private DimensionEnum dimensionEnum;
        private CelestialObjectClass objectClass = CelestialObjectClass.PLANET;

        Builder() {}

        Builder(CelestialObjectIdentity source) {
            if (source == null) return;
            this.id = source.id();
            this.name = source.name();
            this.nameKey = source.nameKey();
            this.parentId = source.parentId();
            this.dimensionEnum = source.dimensionEnum();
            this.objectClass = source.objectClass();
        }

        Builder id(String value) {
            this.id = value;
            return this;
        }

        Builder name(String value) {
            this.name = value;
            return this;
        }

        Builder nameKey(String value) {
            this.nameKey = value;
            return this;
        }

        Builder parentId(String value) {
            this.parentId = value;
            return this;
        }

        Builder dimensionEnum(DimensionEnum value) {
            this.dimensionEnum = value;
            if (value != null) {
                if (this.id == null) this.id = value.name()
                    .toLowerCase();
                if (this.name == null) this.name = value.getName();
                if (this.nameKey == null) this.nameKey = value.getTranslationKey();
            }
            return this;
        }

        Builder objectClass(CelestialObjectClass value) {
            this.objectClass = value == null ? CelestialObjectClass.PLANET : value;
            return this;
        }

        CelestialObjectIdentity build() {
            return new CelestialObjectIdentity(id, name, nameKey, parentId, dimensionEnum, objectClass);
        }
    }
}

@Desugar
record CelestialOrbitDefinition(OrbitalParams orbitalParams, AbsolutePosition absolutePosition) {

    CelestialOrbitDefinition {
        orbitalParams = java.util.Objects.requireNonNull(orbitalParams, "orbitalParams");
    }

    static CelestialOrbitDefinition stationary() {
        return new CelestialOrbitDefinition(OrbitalParams.circular(0.0, 0.0), null);
    }

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private OrbitalParams orbitalParams = OrbitalParams.circular(0.0, 0.0);
        private AbsolutePosition absolutePosition;

        Builder() {}

        Builder(CelestialOrbitDefinition source) {
            if (source == null) return;
            this.orbitalParams = source.orbitalParams();
            this.absolutePosition = source.absolutePosition();
        }

        Builder orbitalParams(OrbitalParams value) {
            this.orbitalParams = java.util.Objects.requireNonNull(value, "orbitalParams");
            return this;
        }

        Builder circularOrbit(double radius, double orbitSpeed) {
            this.orbitalParams = OrbitalParams.circular(radius, orbitSpeed);
            return this;
        }

        Builder circularOrbit(double radius, double orbitSpeed, double meanAnomalyAtEpoch) {
            this.orbitalParams = OrbitalParams.circular(radius, orbitSpeed, meanAnomalyAtEpoch);
            return this;
        }

        Builder absolutePosition(double x, double y) {
            this.absolutePosition = new AbsolutePosition(x, y);
            return this;
        }

        CelestialOrbitDefinition build() {
            return new CelestialOrbitDefinition(orbitalParams, absolutePosition);
        }
    }
}

@Desugar
record CelestialVisualProfile(ResourceLocation texture, double spriteSize) {

    static final CelestialVisualProfile NONE = new CelestialVisualProfile(null, 0.0);

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private ResourceLocation texture;
        private double spriteSize;

        Builder() {}

        Builder(CelestialVisualProfile source) {
            if (source == null) return;
            this.texture = source.texture();
            this.spriteSize = source.spriteSize();
        }

        Builder texture(ResourceLocation value) {
            this.texture = value;
            return this;
        }

        Builder texture(String modid, String path) {
            this.texture = new ResourceLocation(modid, path);
            return this;
        }

        Builder texture(String path) {
            this.texture = GalaxiaAPI.LocationGalaxia(path);
            return this;
        }

        Builder spriteSize(double value) {
            this.spriteSize = value;
            return this;
        }

        CelestialVisualProfile build() {
            return new CelestialVisualProfile(texture, spriteSize);
        }
    }
}
