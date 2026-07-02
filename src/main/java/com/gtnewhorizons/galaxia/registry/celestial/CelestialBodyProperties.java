package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizons.galaxia.compat.GTCompat;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;

public record CelestialBodyProperties(boolean visitable, boolean canCreateStation, boolean canCreateOutpost,
    double localGravityG, double massEarthRelative, double orbitalRadiusEarthRelative, double radiusEarthRelative,
    double standardGravitationalParameter, double sphereOfInfluenceRadius, double parkingOrbitRadius, String oreProfile,
    List<String> gtOreVeinIds, double radiation, double temperature, double surfacePressurePa,
    double starmapAtmosphericDrag, List<AtmosphereIngredient> atmosphereIngredients,
    CelestialBodyProperties atmosphereCompositionSource, AsteroidFieldProfile asteroidFieldProfile,
    AsteroidNodeKind asteroidNodeKind, AsteroidSizeClass asteroidSizeClass, Map<String, String> metadata) {

    public CelestialBodyProperties {
        localGravityG = requireNonNegativeFinite("localGravityG", localGravityG);
        massEarthRelative = requireNonNegativeFinite("massEarthRelative", massEarthRelative);
        orbitalRadiusEarthRelative = requireNonNegativeFinite("orbitalRadiusEarthRelative", orbitalRadiusEarthRelative);
        radiusEarthRelative = requireNonNegativeFinite("radiusEarthRelative", radiusEarthRelative);
        standardGravitationalParameter = requireNonNegativeFinite(
            "standardGravitationalParameter",
            standardGravitationalParameter);
        sphereOfInfluenceRadius = requireNonNegativeFinite("sphereOfInfluenceRadius", sphereOfInfluenceRadius);
        parkingOrbitRadius = requireNonNegativeFinite("parkingOrbitRadius", parkingOrbitRadius);
        radiation = requireNonNegativeFinite("radiation", radiation);
        temperature = requireNonNegativeFinite("temperature", temperature);
        surfacePressurePa = requireNonNegativeFinite("surfacePressurePa", surfacePressurePa);
        starmapAtmosphericDrag = requirePositiveFinite("starmapAtmosphericDrag", starmapAtmosphericDrag);
        if (oreProfile == null) oreProfile = "";
        if (metadata == null) metadata = Collections.emptyMap();
        else metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (gtOreVeinIds == null) gtOreVeinIds = List.of();
        else gtOreVeinIds = Collections.unmodifiableList(new ArrayList<>(gtOreVeinIds));
        if (atmosphereIngredients == null) atmosphereIngredients = List.of();
        else {
            List<AtmosphereIngredient> ingredients = new ArrayList<>(atmosphereIngredients.size());
            for (AtmosphereIngredient ingredient : atmosphereIngredients) {
                if (ingredient == null) {
                    throw new IllegalArgumentException("atmosphere ingredient cannot be null");
                }
                ingredients.add(ingredient);
            }
            atmosphereIngredients = Collections.unmodifiableList(ingredients);
        }
        if (atmosphereCompositionSource != null) {
            if (!atmosphereIngredients.isEmpty()) {
                throw new IllegalStateException("Atmosphere ingredients and composition source are mutually exclusive");
            }
            if (atmosphereCompositionSource.atmosphereCompositionSource() != null) {
                throw new IllegalStateException("Atmosphere composition source must define its own ingredients");
            }
            atmosphereIngredients = atmosphereCompositionSource.atmosphereIngredients();
        }
        if (surfacePressurePa > 0.0 && atmosphereIngredients.isEmpty()) {
            throw new IllegalStateException(
                "Positive atmosphere pressure requires atmosphere ingredients or composition source");
        }
        if (surfacePressurePa == 0.0 && (!atmosphereIngredients.isEmpty() || atmosphereCompositionSource != null)) {
            throw new IllegalStateException("Atmosphere composition requires positive surface pressure");
        }
        if ((asteroidNodeKind == null) != (asteroidSizeClass == null)) {
            throw new IllegalStateException("Asteroid node kind and size class must be set together");
        }
    }

    /**
     * One weighted component of a body's atmosphere. Weights are relative fractions; they do not need to sum to any
     * specific value.
     */
    public record AtmosphereIngredient(@Nonnull Fluid fluid, double weight) {

        public AtmosphereIngredient {
            if (fluid == null) {
                throw new IllegalArgumentException("Atmosphere ingredient fluid cannot be null");
            }
            weight = requirePositiveFinite("atmosphere ingredient weight", weight);
        }
    }

    public boolean hasGtOreVeinIds() {
        return !gtOreVeinIds.isEmpty();
    }

    public List<ItemStack> getResolvedGtVeinOreStacks() {
        if (gtOreVeinIds.isEmpty()) return List.of();
        return GTCompat.getGtVeinOreStacks(gtOreVeinIds.toArray(new String[0]));
    }

    // TODO: come up with some kind of atmosphere recipe autogen that accounts for weights and atmosphere density (low
    // pressure = hard to capture)
    public double atmosphereWeightedAverage(@Nonnull ToDoubleFunction<Fluid> valueProvider) {
        if (surfacePressurePa <= 0.0 || atmosphereIngredients.isEmpty()) return 0.0;

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (AtmosphereIngredient ingredient : atmosphereIngredients) {
            double weight = ingredient.weight();
            weightedSum += valueProvider.applyAsDouble(ingredient.fluid()) * weight;
            totalWeight += weight;
        }
        return weightedSum / totalWeight;
    }

    private static double requireNonNegativeFinite(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be a finite non-negative value");
        }
        return value;
    }

    private static double requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be a finite positive value");
        }
        return value;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean visitable;
        private boolean canCreateStation;
        private boolean canCreateOutpost;
        private double localGravityG;
        private double massEarthRelative;
        private double orbitalRadiusEarthRelative;
        private double radiusEarthRelative;
        private double standardGravitationalParameter;
        private double sphereOfInfluenceRadius;
        private double parkingOrbitRadius;
        private String oreProfile = "";
        private final List<String> resolvedGtOreVeinIds = new ArrayList<>();
        private double radiation;
        private double temperature;
        private double surfacePressurePa;
        private double starmapAtmosphericDrag = 1.0;
        private final List<AtmosphereIngredient> atmosphereIngredients = new ArrayList<>();
        private CelestialBodyProperties atmosphereCompositionSource;
        private AsteroidFieldProfile asteroidFieldProfile;
        private AsteroidNodeKind asteroidNodeKind;
        private AsteroidSizeClass asteroidSizeClass;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder() {}

        public Builder(CelestialBodyProperties source) {
            if (source == null) return;
            this.visitable = source.visitable;
            this.canCreateStation = source.canCreateStation;
            this.canCreateOutpost = source.canCreateOutpost;
            this.localGravityG = source.localGravityG;
            this.massEarthRelative = source.massEarthRelative;
            this.orbitalRadiusEarthRelative = source.orbitalRadiusEarthRelative;
            this.radiusEarthRelative = source.radiusEarthRelative;
            this.standardGravitationalParameter = source.standardGravitationalParameter;
            this.sphereOfInfluenceRadius = source.sphereOfInfluenceRadius;
            this.parkingOrbitRadius = source.parkingOrbitRadius;
            this.oreProfile = source.oreProfile;
            this.resolvedGtOreVeinIds.addAll(source.gtOreVeinIds);
            this.radiation = source.radiation;
            this.temperature = source.temperature;
            this.surfacePressurePa = source.surfacePressurePa;
            this.starmapAtmosphericDrag = source.starmapAtmosphericDrag;
            this.atmosphereCompositionSource = source.atmosphereCompositionSource;
            this.asteroidFieldProfile = source.asteroidFieldProfile;
            this.asteroidNodeKind = source.asteroidNodeKind;
            this.asteroidSizeClass = source.asteroidSizeClass;
            if (source.atmosphereCompositionSource == null) {
                this.atmosphereIngredients.addAll(source.atmosphereIngredients);
            }
            this.metadata.putAll(source.metadata);
        }

        public Builder visitable(boolean value) {
            this.visitable = value;
            return this;
        }

        public Builder canCreateStation(boolean value) {
            this.canCreateStation = value;
            return this;
        }

        public Builder canCreateOutpost(boolean value) {
            this.canCreateOutpost = value;
            return this;
        }

        public Builder localGravityG(double value) {
            this.localGravityG = requireNonNegativeFinite("localGravityG", value);
            return this;
        }

        public Builder massEarthRelative(double value) {
            this.massEarthRelative = requireNonNegativeFinite("massEarthRelative", value);
            return this;
        }

        public Builder orbitalRadiusEarthRelative(double value) {
            this.orbitalRadiusEarthRelative = requireNonNegativeFinite("orbitalRadiusEarthRelative", value);
            return this;
        }

        public Builder radiusEarthRelative(double value) {
            this.radiusEarthRelative = requireNonNegativeFinite("radiusEarthRelative", value);
            return this;
        }

        public Builder standardGravitationalParameter(double value) {
            this.standardGravitationalParameter = requireNonNegativeFinite("standardGravitationalParameter", value);
            return this;
        }

        public Builder sphereOfInfluenceRadius(double value) {
            this.sphereOfInfluenceRadius = requireNonNegativeFinite("sphereOfInfluenceRadius", value);
            return this;
        }

        public Builder parkingOrbitRadius(double value) {
            this.parkingOrbitRadius = requireNonNegativeFinite("parkingOrbitRadius", value);
            return this;
        }

        public Builder oreProfile(String value) {
            this.oreProfile = value == null ? "" : value;
            return this;
        }

        public Builder gtOreVeinIds(@Nonnull String... veinIds) {
            for (String veinId : veinIds) {
                if (veinId != null) resolvedGtOreVeinIds.add(veinId);
            }
            return this;
        }

        public Builder radiation(double value) {
            this.radiation = requireNonNegativeFinite("radiation", value);
            return this;
        }

        public Builder temperature(double value) {
            this.temperature = requireNonNegativeFinite("temperature", value);
            return this;
        }

        /**
         * Defines surface pressure in pascals. {@code pressure == 0} means no atmosphere.
         *
         * <p>
         * Pressure is per body and is never copied by atmosphere composition override. Future atmosphere collection
         * uses
         * pressure to scale compressor difficulty/throughput.
         */
        public Builder surfacePressurePa(double value) {
            this.surfacePressurePa = requireNonNegativeFinite("surfacePressurePa", value);
            return this;
        }

        /**
         * Defines a positive multiplier for future starmap flight planning through atmosphere.
         *
         * <p>
         * TODO: Wire this into starmap launch/landing/trajectory calculations. Keep it separate from dimension movement
         * air resistance, which controls in-world player/entity speed behavior.
         */
        public Builder starmapAtmosphericDrag(double value) {
            this.starmapAtmosphericDrag = requirePositiveFinite("starmapAtmosphericDrag", value);
            return this;
        }

        /**
         * Adds a weighted atmosphere component. Use this with {@link #surfacePressurePa(double)}; positive pressure
         * cannot be built without at least one component, and components cannot be built for pressure {@code 0}.
         */
        public Builder addAtmosphereIngredient(@Nonnull Fluid fluid, double weight) {
            if (atmosphereCompositionSource != null) {
                throw new IllegalStateException("Cannot add atmosphere ingredients after setting a composition source");
            }
            atmosphereIngredients.add(new AtmosphereIngredient(fluid, weight));
            return this;
        }

        /**
         * Reuses another body's atmosphere composition and prevents registering atmosphere fluid. Does not copy surface
         * pressure to allow balancing.
         */
        public Builder copyAtmosphereCompositionFrom(@Nonnull CelestialBodyProperties source) {
            if (!atmosphereIngredients.isEmpty()) {
                throw new IllegalStateException("Cannot set a composition source after adding atmosphere ingredients");
            }
            this.atmosphereCompositionSource = source;
            return this;
        }

        public Builder asteroidFieldProfile(@Nonnull AsteroidFieldProfile value) {
            this.asteroidFieldProfile = value;
            return this;
        }

        public Builder asteroidMetadata(@Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass) {
            this.asteroidNodeKind = kind;
            this.asteroidSizeClass = sizeClass;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder clearMetadata() {
            this.metadata.clear();
            return this;
        }

        public Builder orbitalGravity(double standardGravitationalParameter, double sphereOfInfluenceRadius) {
            return standardGravitationalParameter(standardGravitationalParameter)
                .sphereOfInfluenceRadius(sphereOfInfluenceRadius);
        }

        public CelestialBodyProperties build() {
            return new CelestialBodyProperties(
                visitable,
                canCreateStation,
                canCreateOutpost,
                localGravityG,
                massEarthRelative,
                orbitalRadiusEarthRelative,
                radiusEarthRelative,
                standardGravitationalParameter,
                sphereOfInfluenceRadius,
                parkingOrbitRadius,
                oreProfile,
                resolvedGtOreVeinIds,
                radiation,
                temperature,
                surfacePressurePa,
                starmapAtmosphericDrag,
                atmosphereIngredients,
                atmosphereCompositionSource,
                asteroidFieldProfile,
                asteroidNodeKind,
                asteroidSizeClass,
                metadata);
        }
    }
}
