package com.gtnewhorizons.galaxia.registry.orbital;

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

        public double mu() {
            if (properties == null) return 0.0;
            return Math.max(0.0, properties.standardGravitationalParameter());
        }

        public double getHohmannTof(OrbitalCelestialBody source, OrbitalCelestialBody dest, OrbitalCelestialBody root,
            double time) {
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
