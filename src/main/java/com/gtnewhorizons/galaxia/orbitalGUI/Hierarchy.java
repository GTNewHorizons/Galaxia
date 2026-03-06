package com.gtnewhorizons.galaxia.orbitalGUI;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.LocationGalaxia;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public class Hierarchy {

    @Desugar
    public record OrbitalParams(double semiMajorAxis, double eccentricity, double inclination,
        double longitudeOfAscendingNode, double argumentOfPeriapsis, double meanAnomalyAtEpoch) {

        public double apogee() {
            return semiMajorAxis * (1 + eccentricity);
        }

        public double perigee() {
            return semiMajorAxis * (1 - eccentricity);
        }
    }

    public enum CelestialType {
        BLACK_HOLE,
        STAR,
        PLANET,
        MOON,
        ASTEROID_BELT,
        COMET
    }

    /** Main Recursive record */
    @Desugar
    public record OrbitalCelestialBody(String name, int dimensionId, DimensionEnum dimensionEnum, CelestialType type,
        OrbitalParams orbitalParams, ResourceLocation texture, double spriteSize, List<OrbitalCelestialBody> children) {

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private String name;
            private DimensionEnum dimensionEnum;
            private int dimensionId;
            private CelestialType type = CelestialType.PLANET;
            private OrbitalParams orbitalParams = new OrbitalParams(1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            private ResourceLocation texture = null;
            private double spriteSize = 0.0;
            private final List<Builder> childBuilders = new ArrayList<>();

            public Builder dimension(DimensionEnum de) {
                this.dimensionEnum = Objects.requireNonNull(de);
                this.name = de.getName();
                this.dimensionId = de.getId();
                return this;
            }

            public Builder type(CelestialType type) {
                this.type = type;
                return this;
            }

            public Builder orbital(OrbitalParams params) {
                this.orbitalParams = params;
                return this;
            }

            public Builder apogeePerigee(double apogee, double perigee) {
                double a = (apogee + perigee) / 2.0;
                double e = (apogee - perigee) / (apogee + perigee);
                this.orbitalParams = new OrbitalParams(a, e, 0, 0, 0, 0);
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

            public Builder textureAndSize(String path, double size) {
                this.texture = LocationGalaxia(path);
                this.spriteSize = size;
                return this;
            }

            public Builder textureAndSize(ResourceLocation path, double size) {
                this.texture = path;
                this.spriteSize = size;
                return this;
            }

            public Builder spriteSize(double size) {
                this.spriteSize = size;
                return this;
            }

            public Builder addChild(Consumer<Builder> childConfig) {
                Builder child = new Builder();
                childConfig.accept(child);
                childBuilders.add(child);
                return this;
            }

            public OrbitalCelestialBody build() {
                List<OrbitalCelestialBody> children = childBuilders.stream()
                    .map(Builder::build)
                    .collect(Collectors.toList());

                return new OrbitalCelestialBody(
                    name,
                    dimensionId,
                    dimensionEnum,
                    type,
                    orbitalParams,
                    texture,
                    spriteSize,
                    children);
            }
        }
    }
}
