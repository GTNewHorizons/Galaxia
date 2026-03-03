package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Collections;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;

/**
 * Data record holding terrain features
 */
@Desugar
public record TerrainFeature(TerrainPreset preset, double height, double width, Map<String, Object> customParams) {

    public TerrainFeature {
        customParams = Collections.unmodifiableMap(customParams);
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
        return "TerrainFeature{" + preset + ", height=" + height + "}";
    }
}
