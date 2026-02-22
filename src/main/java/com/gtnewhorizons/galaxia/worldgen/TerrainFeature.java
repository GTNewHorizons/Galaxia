package com.gtnewhorizons.galaxia.worldgen;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TerrainFeature {

    private final TerrainPreset preset;
    private final double frequency;
    private final double height;
    private final double width;
    private final int minHeight;
    private final int variation;
    private final int depth;
    private final Map<String, Object> customParams;

    TerrainFeature(TerrainPreset preset, double frequency, double height, double width, int minHeight, int variation,
        int depth, Map<String, Object> customParams) {
        this.preset = preset;
        this.frequency = frequency;
        this.height = height;
        this.width = width;
        this.minHeight = minHeight;
        this.variation = variation;
        this.depth = depth;
        this.customParams = Collections.unmodifiableMap(new HashMap<>(customParams));
    }

    public TerrainPreset getPreset() {
        return preset;
    }

    public double getFrequency() {
        return frequency;
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getVariation() {
        return variation;
    }

    public int getDepth() {
        return depth;
    }

    public Map<String, Object> getCustomParams() {
        return customParams;
    }

    public Object getCustom(String key) {
        return customParams.get(key);
    }

    public <T> T getCustom(String key, Class<T> type) {
        Object val = customParams.get(key);
        return type.isInstance(val) ? type.cast(val) : null;
    }

    @Override
    public String toString() {
        return "TerrainFeature{" + preset + ", freq=" + frequency + ", height=" + height + ", depth=" + depth + "}";
    }
}
