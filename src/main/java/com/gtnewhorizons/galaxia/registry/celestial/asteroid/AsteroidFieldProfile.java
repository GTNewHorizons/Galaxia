package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public record AsteroidFieldProfile(long seedSalt, int generationVersion, int totalNodes, int largeCount,
    int mediumCount, int smallCount, double innerOrbitalRadius, double outerOrbitalRadius,
    double placementConnectionRadius, @Nonnull AsteroidOreProfilePool oreProfilePool,
    @Nonnull List<AuthoredAsteroidDefinition> authoredAsteroids) {

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
        placementConnectionRadius = requireNonNegativeFinite("placementConnectionRadius", placementConnectionRadius);
        if (oreProfilePool == null)
            throw new IllegalStateException("Asteroid field profile requires an ore profile pool");
        if (authoredAsteroids == null) {
            authoredAsteroids = List.of();
        }
        List<AuthoredAsteroidDefinition> copiedAuthoredAsteroids = new ArrayList<>(authoredAsteroids.size());
        Set<Integer> seenAuthoredIndexes = new HashSet<>();
        for (AuthoredAsteroidDefinition definition : authoredAsteroids) {
            if (definition == null) {
                throw new IllegalArgumentException("authored asteroid definition cannot be null");
            }
            if (AsteroidSlotRanges.isGeneratedSlot(definition.index())) {
                throw new IllegalArgumentException(
                    "authored asteroid index must be in a reserved authored asteroid slot range");
            }
            if (!seenAuthoredIndexes.add(definition.index())) {
                throw new IllegalArgumentException("duplicate authored asteroid index: " + definition.index());
            }
            copiedAuthoredAsteroids.add(definition);
        }
        authoredAsteroids = Collections.unmodifiableList(copiedAuthoredAsteroids);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<AuthoredAsteroidDefinition> authoredAsteroid(int index) {
        return authoredAsteroids.stream()
            .filter(definition -> definition.index() == index)
            .findFirst();
    }

    public List<AsteroidOreProfile> oreProfiles() {
        return oreProfilePool.profiles();
    }

    public boolean hasNodeIndex(int index) {
        if (AsteroidSlotRanges.isGeneratedSlot(index)) {
            return AsteroidSlotRanges.generatedOrdinal(index) < totalNodes;
        }
        return authoredAsteroid(index).isPresent();
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
        private double placementConnectionRadius = Double.NaN;
        private final AsteroidOreProfilePool.Builder oreProfilePoolBuilder = AsteroidOreProfilePool.builder();
        private final List<AuthoredAsteroidDefinition> authoredAsteroids = new ArrayList<>();

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

        public Builder placementConnectionRadius(double value) {
            this.placementConnectionRadius = requireNonNegativeFinite("placementConnectionRadius", value);
            return this;
        }

        public Builder oreProfile(@Nonnull AsteroidOreProfile value) {
            return oreProfile(value, 1.0);
        }

        public Builder oreProfile(@Nonnull AsteroidOreProfile value, double weight) {
            this.oreProfilePoolBuilder.profile(value, weight);
            return this;
        }

        public Builder authoredAsteroid(int index, AsteroidNodeKind kind, String displayName,
            DiscoveryState initialDetectionState) {
            this.authoredAsteroids.add(new AuthoredAsteroidDefinition(index, kind, displayName, initialDetectionState));
            return this;
        }

        public Builder authoredAsteroid(@Nonnull AuthoredAsteroidDefinition value) {
            this.authoredAsteroids.add(value);
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
                placementConnectionRadius,
                oreProfilePoolBuilder.build(),
                authoredAsteroids);
        }
    }
}
