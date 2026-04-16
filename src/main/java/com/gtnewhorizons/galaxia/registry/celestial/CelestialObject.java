package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalParams;

public record CelestialObject(CelestialObjectId id, String name, String nameKey, CelestialObjectId parentId,
    DimensionEnum dimensionEnum, Class objectClass, OrbitalParams orbitalParams,
    OrbitalMechanics.AbsolutePosition absolutePosition, ResourceLocation texture, double spriteSize,
    CelestialBodyProperties properties) {

    public enum Class {
        GALAXY,
        STAR,
        GAS_GIANT,
        PLANET,
        MOON,
        ASTEROID,
        ASTEROID_BELT,
        STATION,
        BLACK_HOLE,
        COMET
    }

    public CelestialObject {
        if (id == null) throw new IllegalStateException("Celestial object id is required");
        if (name == null || name.isEmpty()) throw new IllegalStateException("Celestial object name is required");
        objectClass = objectClass == null ? Class.PLANET : objectClass;
        orbitalParams = orbitalParams == null ? OrbitalParams.circular(0.0, 0.0) : orbitalParams;
        properties = properties == null ? CelestialBodyProperties.builder()
            .build() : properties;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public double mu() {
        CelestialBodyProperties props = properties();
        if (props == null) return 0.0;
        return Math.max(0.0, props.standardGravitationalParameter());
    }

    public double getHohmannTof(CelestialObject source, CelestialObject dest, CelestialObject root, double time) {
        OrbitalMechanics.OrbitalState aState = OrbitalMechanics.resolveWorldState(root, this, time);
        OrbitalMechanics.OrbitalState sState = OrbitalMechanics.resolveWorldState(root, source, time);
        OrbitalMechanics.OrbitalState dState = OrbitalMechanics.resolveWorldState(root, dest, time);
        if (aState == null || sState == null || dState == null) return -1.0;
        double r1 = Math.hypot(sState.x() - aState.x(), sState.y() - aState.y());
        double r2 = Math.hypot(dState.x() - aState.x(), dState.y() - aState.y());
        double bodyMu = mu();
        double sma = (r1 + r2) * 0.5;
        return Math.PI * Math.sqrt(sma * sma * sma / Math.max(1e-6, bodyMu));
    }

    public static double computePeriapsis(double rx, double ry, double vx, double vy, double mu) {
        double r = Math.hypot(rx, ry);
        if (r < 1e-10) return 0.0;
        double v2 = vx * vx + vy * vy;
        double energy = 0.5 * v2 - mu / r;
        double h = rx * vy - ry * vx;
        double p = h * h / Math.max(1e-30, mu);
        double disc = 1.0 + 2.0 * energy * p / mu;
        double ecc = Math.sqrt(Math.max(0.0, disc));
        return p / (1.0 + ecc);
    }

    public String displayName() {
        String nameKey = nameKey();
        if (nameKey != null && !nameKey.isEmpty()) {
            String translated = StatCollector.translateToLocal(nameKey);
            if (!nameKey.equals(translated)) return translated;
        }
        return name();
    }

    public boolean isLandable() {
        return switch (this.objectClass()) {
            case PLANET, MOON, ASTEROID -> this.properties()
                .visitable();
            default -> false;
        };
    }

    public static final class Builder {

        private CelestialObjectId id;
        private String name;
        private String nameKey;
        private CelestialObjectId parentId;
        private DimensionEnum dimensionEnum;
        private Class objectClass = Class.PLANET;
        private OrbitalParams orbitalParams = OrbitalParams.circular(0.0, 0.0);
        private OrbitalMechanics.AbsolutePosition absolutePosition;
        private ResourceLocation texture;
        private double spriteSize;
        private CelestialBodyProperties properties = CelestialBodyProperties.builder()
            .build();

        public Builder() {}

        public Builder(CelestialObject source) {
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

        public Builder objectClass(Class value) {
            this.objectClass = value == null ? Class.PLANET : value;
            return this;
        }

        public Builder orbitalParams(@Nonnull OrbitalParams value) {
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
            this.absolutePosition = new OrbitalMechanics.AbsolutePosition(x, y);
            return this;
        }

        public Builder absolutePosition(OrbitalMechanics.AbsolutePosition value) {
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

        public CelestialObject build() {
            return new CelestialObject(
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
