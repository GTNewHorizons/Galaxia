package com.gtnewhorizons.galaxia.registry.celestial;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.LocationGalaxia;

import java.util.Objects;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

@Desugar
public record CelestialObjectRegistration(String id, String name, String nameKey, String parentId,
    DimensionEnum dimensionEnum, CelestialObjectClass objectClass, OrbitalParams orbitalParams,
    AbsolutePosition absolutePosition, ResourceLocation texture, double spriteSize,
    CelestialBodyProperties properties) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String name;
        private String nameKey;
        private String parentId;
        private DimensionEnum dimensionEnum;
        private CelestialObjectClass objectClass = CelestialObjectClass.PLANET;
        private OrbitalParams orbitalParams = OrbitalParams.circular(0.0, 0.0);
        private AbsolutePosition absolutePosition;
        private ResourceLocation texture;
        private double spriteSize;
        private CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .build();

        public Builder id(String value) {
            this.id = value;
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

        public Builder parent(String value) {
            this.parentId = value;
            return this;
        }

        public Builder dimension(DimensionEnum value) {
            this.dimensionEnum = value;
            if (this.id == null) this.id = value.name()
                .toLowerCase();
            if (this.name == null) this.name = value.getName();
            if (this.nameKey == null) this.nameKey = value.getTranslationKey();
            return this;
        }

        public Builder objectClass(CelestialObjectClass value) {
            this.objectClass = value;
            return this;
        }

        public Builder orbital(OrbitalParams value) {
            this.orbitalParams = Objects.requireNonNull(value);
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

        public Builder texture(ResourceLocation value) {
            this.texture = value;
            return this;
        }

        public Builder texture(String modid, String path) {
            this.texture = new ResourceLocation(modid, path);
            return this;
        }

        public Builder texture(String path) {
            this.texture = LocationGalaxia(path);
            return this;
        }

        public Builder spriteSize(double value) {
            this.spriteSize = value;
            return this;
        }

        public Builder properties(CelestialBodyProperties value) {
            this.properties = Objects.requireNonNull(value);
            return this;
        }

        public CelestialObjectRegistration build() {
            if (id == null || id.isEmpty()) throw new IllegalStateException("Celestial object id is required");
            if (name == null || name.isEmpty()) throw new IllegalStateException("Celestial object name is required");

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
