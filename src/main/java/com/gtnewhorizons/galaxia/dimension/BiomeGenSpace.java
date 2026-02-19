package com.gtnewhorizons.galaxia.dimension;

import net.minecraft.world.biome.BiomeGenBase;

public class BiomeGenSpace extends BiomeGenBase {
    private final boolean generateBedrock;

    public BiomeGenSpace(int id, BiomeGenBuilder b) {
        super(id);

        this.setBiomeName(b.name);
        this.setHeight(b.height);
        this.setTemperatureRainfall(b.temperature, b.rainfall);

        // TODO: Expand space biomes to also store block meta
        this.topBlock = b.topBlock.block();
        this.fillerBlock = b.fillerBlock.block();

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
}
