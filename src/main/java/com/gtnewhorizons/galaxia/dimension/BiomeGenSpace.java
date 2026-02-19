package com.gtnewhorizons.galaxia.dimension;

import net.minecraft.world.biome.BiomeGenBase;

public class BiomeGenSpace extends BiomeGenBase {
    private final boolean generateBedrock;
    private final int topBlockMeta;
    private final int fillerBlockMeta;

    public BiomeGenSpace(int id, BiomeGenBuilder b) {
        super(id);

        this.setBiomeName(b.name);
        this.setHeight(b.height);
        this.setTemperatureRainfall(b.temperature, b.rainfall);

        this.topBlock = b.topBlock.block();
        this.fillerBlock = b.fillerBlock.block();
        this.topBlockMeta = b.topBlock.meta();
        this.fillerBlockMeta = b.fillerBlock.meta();

        this.spawnableCaveCreatureList = b.mobsCave;
        this.spawnableCreatureList = b.mobsGeneral;
        this.spawnableMonsterList = b.mobsMonster;
        this.spawnableWaterCreatureList = b.mobsWater;
        this.flowers = b.flowers;

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
}
