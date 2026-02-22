package com.gtnewhorizons.galaxia.dimension;

import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;

public class BiomeGenSpace extends BiomeGenBase {

    private final boolean generateBedrock;
    private final int topBlockMeta;
    private final int fillerBlockMeta;
    private final TerrainConfiguration terrain;
    private final int snowHeight;
    private final BlockMeta snowBlock;
    private final int oceanHeight;
    private final int seabedHeight;
    private final BlockMeta oceanFiller;
    private final BlockMeta oceanSurface;
    private final BlockMeta seabed;

    public BiomeGenSpace(int id, BiomeGenBuilder b) {
        super(id);

        this.setBiomeName(b.name);
        this.setHeight(b.height);
        this.setTemperatureRainfall(b.temperature, b.rainfall);

        this.topBlock = b.topBlock.block();
        this.fillerBlock = b.fillerBlock.block();
        this.topBlockMeta = b.topBlock.meta();
        this.fillerBlockMeta = b.fillerBlock.meta();
        this.snowBlock = b.snowBlock;
        this.snowHeight = b.snowHeight;
        this.oceanHeight = b.oceanHeight;
        this.seabedHeight = b.seabedHeight;
        this.oceanFiller = b.oceanFiller;
        this.oceanSurface = b.oceanSurface;
        this.seabed = b.seabed;

        this.spawnableCaveCreatureList = b.mobsCave;
        this.spawnableCreatureList = b.mobsGeneral;
        this.spawnableMonsterList = b.mobsMonster;
        this.spawnableWaterCreatureList = b.mobsWater;
        this.flowers = b.flowers;

        this.terrain = b.terrain != null ? b.terrain
            : TerrainConfiguration.builder()
                .build();
        this.generateBedrock = b.generateBedrock;
    }

    public boolean generateBedrock() {
        return generateBedrock;
    }

    public int getTopBlockMeta() {
        return topBlockMeta;
    }

    public int getFillerBlockMeta() {
        return fillerBlockMeta;
    }

    public TerrainConfiguration getTerrain() {
        return terrain;
    }

    public BlockMeta getSnowBlock() {
        return snowBlock;
    }

    public int getSnowHeight() {
        return snowHeight;
    }

    public int getOceanHeight() {
        return oceanHeight;
    }

    public BlockMeta getOceanFiller() {
        return oceanFiller;
    }

    public BlockMeta getOceanSurface() {
        return oceanSurface;
    }

    public BlockMeta getSeabed() {
        return seabed;
    }

    public int getSeabedHeight() {
        return seabedHeight;
    }
}
