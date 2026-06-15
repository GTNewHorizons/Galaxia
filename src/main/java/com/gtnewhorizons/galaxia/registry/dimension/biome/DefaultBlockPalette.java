package com.gtnewhorizons.galaxia.registry.dimension.biome;

import net.minecraft.init.Blocks;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationLayers;

public class DefaultBlockPalette implements BiomeBlockPalette {

    private final ImmutableBlockMeta topBlock = new BlockMeta(Blocks.grass);
    private final StratificationLayers fillerBlocks = new StratificationLayers(Blocks.stone).freeze();
    private final ImmutableBlockMeta snowBlock = new BlockMeta(Blocks.snow);
    private final ImmutableBlockMeta oceanFiller = new BlockMeta(Blocks.water);
    private final ImmutableBlockMeta oceanSurface = new BlockMeta(Blocks.sand);
    private final ImmutableBlockMeta seabed = new BlockMeta(Blocks.gravel);
    private final ImmutableBlockMeta oceanCrackBlock = new BlockMeta(Blocks.lava);

    @Override
    public ImmutableBlockMeta getTopBlock() {
        return topBlock;
    }

    @Override
    public StratificationLayers getFillerBlocks() {
        return fillerBlocks;
    }

    @Override
    public ImmutableBlockMeta getSnowBlock() {
        return snowBlock;
    }

    @Override
    public ImmutableBlockMeta getOceanFiller() {
        return oceanFiller;
    }

    @Override
    public ImmutableBlockMeta getOceanSurface() {
        return oceanSurface;
    }

    @Override
    public ImmutableBlockMeta getSeabed() {
        return seabed;
    }

    @Override
    public ImmutableBlockMeta getOceanCrackBlock() {
        return oceanCrackBlock;
    }

    @Override
    public int getSnowHeight() {
        return 512;
    }

    @Override
    public int getOceanHeight() {
        return 0;
    }

    @Override
    public int getSeabedHeight() {
        return 0;
    }

    @Override
    public int getSurfaceThickness() {
        return 1;
    }

    @Override
    public float getOceanCrackThickness() {
        return 0;
    }

    @Override
    public int getOceanCrackComplexity() {
        return 0;
    }
}
