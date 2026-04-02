package com.gtnewhorizons.galaxia.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class Hierarchy {

    private Hierarchy() {}

    @Desugar
    public record OrbitalParams(double semiMajorAxis, double eccentricity, double inclination,
        double longitudeOfAscendingNode, double argumentOfPeriapsis, double meanAnomalyAtEpoch, double orbitSpeed) {

        public OrbitalParams(double semiMajorAxis, double eccentricity, double inclination,
            double longitudeOfAscendingNode, double argumentOfPeriapsis, double meanAnomalyAtEpoch) {
            this(
                semiMajorAxis,
                eccentricity,
                inclination,
                longitudeOfAscendingNode,
                argumentOfPeriapsis,
                meanAnomalyAtEpoch,
                0.0);
        }

        public static OrbitalParams circular(double radius, double orbitSpeed) {
            return new OrbitalParams(radius, 0.0, 0.0, 0.0, 0.0, 0.0, orbitSpeed);
        }

        public static OrbitalParams circular(double radius, double orbitSpeed, double meanAnomalyAtEpoch) {
            return new OrbitalParams(radius, 0.0, 0.0, 0.0, 0.0, meanAnomalyAtEpoch, orbitSpeed);
        }

        public double apogee() {
            return semiMajorAxis * (1 + eccentricity);
        }

        public double perigee() {
            return semiMajorAxis * (1 - eccentricity);
        }
    }

    @Desugar
    public record AbsolutePosition(double x, double y) {}

    @Desugar
    public record OrbitalCelestialBody(String id, String name, String nameKey, int dimensionId,
        DimensionEnum dimensionEnum, CelestialObjectClass objectClass, OrbitalParams orbitalParams,
        AbsolutePosition absolutePosition, ResourceLocation texture, double spriteSize,
        CelestialBodyProperties properties, List<OrbitalCelestialBody> children) {

        public OrbitalCelestialBody {
            children = children == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(children));
        }

        public String displayName() {
            if (nameKey != null && !nameKey.isEmpty()) {
                String translated = StatCollector.translateToLocal(nameKey);
                if (!nameKey.equals(translated)) return translated;
            }
            return name;
        }

        public boolean hasDimension() {
            return dimensionEnum != null;
        }
    }

    public static final class MetadataBuilder {

        private final Map<String, String> values = new LinkedHashMap<>();

        public MetadataBuilder put(String key, String value) {
            values.put(key, value);
            return this;
        }

        public Map<String, String> build() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
