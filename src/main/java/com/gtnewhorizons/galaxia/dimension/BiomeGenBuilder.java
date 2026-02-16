package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase.FlowerEntry;
import net.minecraft.world.biome.BiomeGenBase.Height;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;

public class BiomeGenBuilder {

    private final int id;

    String name = "unset";
    Height height = new Height(0, 0);
    float temperature = 0.4F;
    float rainfall = 0.0F;

    Block topBlock = Blocks.stone;
    Block fillerBlock = Blocks.stone;

    List<FlowerEntry> flowers = Collections.emptyList();
    List<SpawnListEntry> mobsWater = Collections.emptyList();
    List<SpawnListEntry> mobsCave = Collections.emptyList();
    List<SpawnListEntry> mobsGeneral = Collections.emptyList();
    List<SpawnListEntry> mobsMonster = Collections.emptyList();

    public BiomeGenBuilder(int id) {
        this.id = id;
    }

    public BiomeGenBuilder name(String name) {
        this.name = name;
        return this;
    }

    public BiomeGenBuilder height(float low, float high) {
        this.height = new Height(low, high);
        return this;
    }

    public BiomeGenBuilder temperature(float temp) {
        this.temperature = temp;
        return this;
    }

    public BiomeGenBuilder rainfall(float rain) {
        this.rainfall = rain;
        return this;
    }

    public BiomeGenBuilder topBlock(Block block) {
        this.topBlock = block;
        return this;
    }

    public BiomeGenBuilder fillerBlock(Block block) {
        this.fillerBlock = block;
        return this;
    }

    public BiomeGenBuilder mobsAll(List<SpawnListEntry> list) {
        this.mobsGeneral = list;
        this.mobsMonster = list;
        this.mobsWater = list;
        this.mobsCave = list;
        return this;
    }

    public BiomeGenBuilder mobsGeneral(List<SpawnListEntry> list) {
        this.mobsGeneral = list;
        return this;
    }

    public BiomeGenBuilder mobsMonster(List<SpawnListEntry> list) {
        this.mobsMonster = list;
        return this;
    }

    public BiomeGenBuilder mobsWater(List<SpawnListEntry> list) {
        this.mobsWater = list;
        return this;
    }

    public BiomeGenBuilder mobsCave(List<SpawnListEntry> list) {
        this.mobsCave = list;
        return this;
    }

    public BiomeGenBuilder flowers(List<FlowerEntry> list) {
        this.flowers = list;
        return this;
    }

    public BiomeGenSpace build() {
        return new BiomeGenSpace(id, this);
    }
}
