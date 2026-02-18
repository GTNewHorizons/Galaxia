package com.gtnewhorizons.galaxia.worldgen;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public final class TerrainFeature {

    private final TerrainPreset preset;
    private final double frequency;
    private final double size;
    private final int minHeight;
    private final int variation;
    private final Block topBlock;
    private final Block fillerBlock;
    private final int depth;
    private final Map<String, Object> customParams;

    TerrainFeature(TerrainPreset preset, double frequency, double size, int minHeight, int variation, Block topBlock,
        Block fillerBlock, int depth, Map<String, Object> customParams) {
        this.preset = preset;
        this.frequency = frequency;
        this.size = size;
        this.minHeight = minHeight;
        this.variation = variation;
        this.topBlock = topBlock != null ? topBlock : Blocks.grass; // fallback
        this.fillerBlock = fillerBlock;
        this.depth = depth;
        this.customParams = Collections.unmodifiableMap(new HashMap<>(customParams));
    }

    public TerrainPreset getPreset() {
        return preset;
    }

    public double getFrequency() {
        return frequency;
    }

    public double getSize() {
        return size;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getVariation() {
        return variation;
    }

    public Block getTopBlock() {
        return topBlock;
    }

    public Block getFillerBlock() {
        return fillerBlock;
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
        return "TerrainFeature{" + preset + ", freq=" + frequency + ", size=" + size + ", depth=" + depth + "}";
    }
}
