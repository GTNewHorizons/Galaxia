package com.cleanroommc.galaxia.dimension;

import net.minecraft.world.biome.BiomeGenBase;

public class BiomeGenSpace extends BiomeGenBase {

    public BiomeGenSpace(int id, BiomeGenBuilder b) {
        super(id);

        this.setBiomeName(b.name);
        this.setHeight(b.height);
        this.setTemperatureRainfall(b.temperature, b.rainfall);

        this.topBlock = b.topBlock;
        this.fillerBlock = b.fillerBlock;

        this.spawnableCaveCreatureList = b.mobsCave;
        this.spawnableCreatureList = b.mobsGeneral;
        this.spawnableMonsterList = b.mobsMonster;
        this.spawnableWaterCreatureList = b.mobsWater;
        this.flowers = b.flowers;
    }
}
