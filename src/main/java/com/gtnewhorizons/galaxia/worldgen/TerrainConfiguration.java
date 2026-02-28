package com.gtnewhorizons.galaxia.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to hold config info on terrain features
 */
public final class TerrainConfiguration {

    private final List<TerrainFeature> allFeatures;
    private final List<TerrainFeature> macro;
    private final List<TerrainFeature> meso;
    private final List<TerrainFeature> micro;

    /**
     * Constructor to initalize terrain feature lists
     *
     * @param features
     */
    private TerrainConfiguration(List<TerrainFeature> features) {
        this.allFeatures = Collections.unmodifiableList(new ArrayList<>(features));

        List<TerrainFeature> m = new ArrayList<>();
        List<TerrainFeature> me = new ArrayList<>();
        List<TerrainFeature> mi = new ArrayList<>();

        for (TerrainFeature f : features) {
            switch (f.preset().scale) {
                case MACRO:
                    m.add(f);
                    break;
                case MESO:
                    me.add(f);
                    break;
                case MICRO:
                    mi.add(f);
                    break;
            }
        }

        this.macro = Collections.unmodifiableList(m);
        this.meso = Collections.unmodifiableList(me);
        this.micro = Collections.unmodifiableList(mi);
    }

    /**
     * Getter for all features of any type
     *
     * @return List of all features
     */
    public List<TerrainFeature> getAllFeatures() {
        return allFeatures;
    }

    /**
     * Getter for macro features of any type
     *
     * @return List of macro features
     */
    public List<TerrainFeature> getMacroFeatures() {
        return macro;
    }

    /**
     * Getter for meso features of any type
     *
     * @return List of meso features
     */
    public List<TerrainFeature> getMesoFeatures() {
        return meso;
    }

    /**
     * Getter for micro features of any type
     *
     * @return List of micro features
     */
    public List<TerrainFeature> getMicroFeatures() {
        return micro;
    }

    /**
     * Instantiates a builder for generating new config
     *
     * @return A default builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Class used to build terrain features into a configuration
     */
    public static final class Builder {

        private final List<TerrainFeature> features = new ArrayList<>();

        /**
         * Adds a new feature based on a preset
         *
         * @param preset The preset feature to add
         * @return The feature configurator with this preset added
         */
        public FeatureConfigurator feature(TerrainPreset preset) {
            return new FeatureConfigurator(this, preset);
        }

        /**
         * Builds the Terrain configuration based on current features
         *
         * @return Terrain configuration with features
         */
        public TerrainConfiguration build() {
            return new TerrainConfiguration(features);
        }
    }

    /**
     * Feature Configuration class
     */
    public static final class FeatureConfigurator {

        private final Builder parent;
        private final TerrainPreset preset;

        private double height = -1;
        private double width = -1;
        private double scaleMultiplier = 1.0;
        private final Map<String, Object> custom = new HashMap<>();

        /**
         * Constructs with a parent builder and a preset
         *
         * @param parent
         * @param preset
         */
        FeatureConfigurator(Builder parent, TerrainPreset preset) {
            this.parent = parent;
            this.preset = preset;
        }

        /**
         * Modifies the scale of the feature
         *
         * @param multiplier the scale factor by which to change size
         * @return Configured builder
         */
        public FeatureConfigurator scale(double multiplier) {
            this.scaleMultiplier = multiplier;
            return this;
        }

        /**
         * Sets the feature height
         *
         * @param h Required height
         * @return Configured builder
         */
        public FeatureConfigurator height(double h) {
            this.height = h;
            return this;
        }

        /**
         * Sets the feature width
         *
         * @param w Required width
         * @return Configured builder
         */
        public FeatureConfigurator width(double w) {
            this.width = w;
            return this;
        }

        /**
         * Adds a custom feature based on a key value pair
         *
         * @param key   The key of the addition
         * @param value The value of the addition
         * @return Configured builder
         */
        public FeatureConfigurator custom(String key, Object value) {
            this.custom.put(key, value);
            return this;
        }

        /**
         * The final stage building of the feature itself based on all parameters previously given
         *
         * @return A builder with the feature added
         */
        public Builder endFeature() {
            double finalHeight = (height > 0 ? height : 1) * scaleMultiplier;
            double finalWidth = (width > 0 ? width : 1) * scaleMultiplier;

            TerrainFeature feature = new TerrainFeature(preset, finalHeight, finalWidth, custom);

            parent.features.add(feature);
            return parent;
        }

        /**
         * Adds a feature based on a preset
         *
         * @param nextPreset The next preset to add
         * @return Configured builder
         */
        public FeatureConfigurator feature(TerrainPreset nextPreset) {
            endFeature();
            return parent.feature(nextPreset);
        }
    }
}
