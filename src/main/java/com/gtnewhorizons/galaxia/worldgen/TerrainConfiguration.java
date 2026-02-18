package com.gtnewhorizons.galaxia.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public final class TerrainConfiguration {

    private final List<TerrainFeature> allFeatures;
    private final List<TerrainFeature> macro;
    private final List<TerrainFeature> meso;
    private final List<TerrainFeature> micro;

    private TerrainConfiguration(List<TerrainFeature> features) {
        this.allFeatures = Collections.unmodifiableList(new ArrayList<>(features));

        List<TerrainFeature> m = new ArrayList<>();
        List<TerrainFeature> me = new ArrayList<>();
        List<TerrainFeature> mi = new ArrayList<>();

        for (TerrainFeature f : features) {
            switch (f.getPreset().scale) {
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

    public List<TerrainFeature> getAllFeatures() {
        return allFeatures;
    }

    public List<TerrainFeature> getMacroFeatures() {
        return macro;
    }

    public List<TerrainFeature> getMesoFeatures() {
        return meso;
    }

    public List<TerrainFeature> getMicroFeatures() {
        return micro;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<TerrainFeature> features = new ArrayList<>();

        public FeatureConfigurator feature(TerrainPreset preset) {
            return new FeatureConfigurator(this, preset);
        }

        public TerrainConfiguration build() {
            return new TerrainConfiguration(features);
        }
    }

    public static final class FeatureConfigurator {

        private final Builder parent;
        private final TerrainPreset preset;

        private double frequency = -1;
        private double size = -1;
        private double scaleMultiplier = 1.0;
        private int minHeight = -1;
        private int variation = -1;
        private Block topBlock = null;
        private Block fillerBlock = Blocks.stone;
        private int depth = 30;
        private final Map<String, Object> custom = new HashMap<>();

        FeatureConfigurator(Builder parent, TerrainPreset preset) {
            this.parent = parent;
            this.preset = preset;
        }

        public FeatureConfigurator scale(double multiplier) {
            this.scaleMultiplier = multiplier;
            return this;
        }

        public FeatureConfigurator frequency(double freq) {
            this.frequency = freq;
            return this;
        }

        public FeatureConfigurator size(double s) {
            this.size = s;
            return this;
        }

        public FeatureConfigurator height(int min, int var) {
            this.minHeight = min;
            this.variation = var;
            return this;
        }

        public FeatureConfigurator topBlock(Block block) {
            this.topBlock = block;
            return this;
        }

        public FeatureConfigurator fillerBlock(Block block) {
            this.fillerBlock = block;
            return this;
        }

        public FeatureConfigurator depth(int d) {
            this.depth = d;
            return this;
        }

        public FeatureConfigurator custom(String key, Object value) {
            this.custom.put(key, value);
            return this;
        }

        public Builder endFeature() {
            double finalFreq = (frequency > 0 ? frequency : preset.defaultFrequency) * scaleMultiplier;
            double finalSize = (size > 0 ? size : preset.defaultSize) * scaleMultiplier;
            int finalMinH = minHeight >= 0 ? minHeight : preset.defaultMinHeight;
            int finalVar = variation >= 0 ? variation : preset.defaultVariation;

            TerrainFeature feature = new TerrainFeature(
                preset,
                finalFreq,
                finalSize,
                finalMinH,
                finalVar,
                topBlock,
                fillerBlock,
                depth,
                custom);

            parent.features.add(feature);
            return parent;
        }

        public FeatureConfigurator feature(TerrainPreset nextPreset) {
            endFeature();
            return parent.feature(nextPreset);
        }
    }
}
