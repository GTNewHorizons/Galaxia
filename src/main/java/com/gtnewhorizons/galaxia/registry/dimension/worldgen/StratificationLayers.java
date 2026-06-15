package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class StratificationLayers implements StratificationFunction {

    private static final ImmutableBlockMeta AIR = new BlockMeta(Blocks.air);

    public record Strata(ImmutableBlockMeta blockMeta, int min, int max) { }

    private final List<Strata> strataLayers = new ArrayList<>();
    private boolean frozen = false;

    public StratificationLayers(ImmutableBlockMeta defaultBlock) {
        strataLayers.add(new Strata(defaultBlock, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    public StratificationLayers(Block defaultBlock) {
        this(new BlockMeta(defaultBlock));
    }

    public StratificationLayers freeze() {
        this.frozen = true;
        return this;
    }

    public StratificationLayers addStrataLayer(Block block, int minimumHeight, int maximumHeight) {
        return addStrataLayer(new BlockMeta(block), minimumHeight, maximumHeight);
    }

    public StratificationLayers addStrataLayer(ImmutableBlockMeta blockMeta, int minimumHeight, int maximumHeight) {
        if (frozen) {
            throw new IllegalStateException("Cannot mutate a frozen StratificationPreset");
        }
        strataLayers.add(new Strata(blockMeta, minimumHeight, maximumHeight));
        return this;
    }

    @Override
    public ImmutableBlockMeta getStrataBlock(int height) {
        for (int i = strataLayers.size() - 1; i >= 0; i--) {
            var layer = strataLayers.get(i);

            if (layer.min <= height && layer.max >= height) return layer.blockMeta;
        }

        return AIR;
    }
}
