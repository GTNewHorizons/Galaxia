package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record AsteroidFieldProfile(long seedSalt, int generationVersion, int totalNodes, int largeCount,
    int mediumCount, int smallCount, double innerOrbitalRadius, double outerOrbitalRadius, double satelliteScanRadius,
    List<AsteroidOreProfile> oreProfiles) {

    public AsteroidFieldProfile {
        generationVersion = requirePositive("generationVersion", generationVersion);
        largeCount = requireNonNegative("largeCount", largeCount);
        mediumCount = requireNonNegative("mediumCount", mediumCount);
        smallCount = requireNonNegative("smallCount", smallCount);
        int expectedTotal = largeCount + mediumCount + smallCount;
        if (totalNodes != expectedTotal) {
            throw new IllegalArgumentException("totalNodes must equal the sum of size counts");
        }
        if (totalNodes <= 0) {
            throw new IllegalStateException("Asteroid field profile must generate at least one node");
        }
        innerOrbitalRadius = requirePositiveFinite("innerOrbitalRadius", innerOrbitalRadius);
        outerOrbitalRadius = requirePositiveFinite("outerOrbitalRadius", outerOrbitalRadius);
        if (outerOrbitalRadius <= innerOrbitalRadius) {
            throw new IllegalArgumentException("outerOrbitalRadius must be greater than innerOrbitalRadius");
        }
        satelliteScanRadius = requireNonNegativeFinite("satelliteScanRadius", satelliteScanRadius);
        if (oreProfiles == null || oreProfiles.isEmpty()) {
            throw new IllegalStateException("Asteroid field profile requires at least one ore profile");
        }
        List<AsteroidOreProfile> copiedOreProfiles = new ArrayList<>(oreProfiles.size());
        for (AsteroidOreProfile oreProfile : oreProfiles) {
            copiedOreProfiles.add(Objects.requireNonNull(oreProfile, "ore profile cannot be null"));
        }
        oreProfiles = Collections.unmodifiableList(copiedOreProfiles);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static int requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static double requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be a finite positive value");
        }
        return value;
    }

    private static double requireNonNegativeFinite(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    public static final class Builder {

        private long seedSalt;
        private int generationVersion = 1;
        private int largeCount;
        private int mediumCount;
        private int smallCount;
        private double innerOrbitalRadius;
        private double outerOrbitalRadius;
        private double satelliteScanRadius = Double.NaN;
        private final List<AsteroidOreProfile> oreProfiles = new ArrayList<>();

        public Builder seedSalt(long value) {
            this.seedSalt = value;
            return this;
        }

        public Builder generationVersion(int value) {
            this.generationVersion = requirePositive("generationVersion", value);
            return this;
        }

        public Builder sizeCounts(int large, int medium, int small) {
            this.largeCount = requireNonNegative("largeCount", large);
            this.mediumCount = requireNonNegative("mediumCount", medium);
            this.smallCount = requireNonNegative("smallCount", small);
            return this;
        }

        public Builder radialBand(double innerRadius, double outerRadius) {
            this.innerOrbitalRadius = requirePositiveFinite("innerOrbitalRadius", innerRadius);
            this.outerOrbitalRadius = requirePositiveFinite("outerOrbitalRadius", outerRadius);
            if (outerRadius <= innerRadius) {
                throw new IllegalArgumentException("outerOrbitalRadius must be greater than innerOrbitalRadius");
            }
            return this;
        }

        public Builder satelliteScanRadius(double value) {
            this.satelliteScanRadius = requireNonNegativeFinite("satelliteScanRadius", value);
            return this;
        }

        public Builder oreProfile(AsteroidOreProfile value) {
            this.oreProfiles.add(Objects.requireNonNull(value, "ore profile cannot be null"));
            return this;
        }

        public AsteroidFieldProfile build() {
            int totalNodes = largeCount + mediumCount + smallCount;
            return new AsteroidFieldProfile(
                seedSalt,
                generationVersion,
                totalNodes,
                largeCount,
                mediumCount,
                smallCount,
                innerOrbitalRadius,
                outerOrbitalRadius,
                satelliteScanRadius,
                oreProfiles);
        }
    }
}
