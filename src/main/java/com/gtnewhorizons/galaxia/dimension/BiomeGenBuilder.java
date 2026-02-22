package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase.FlowerEntry;
import net.minecraft.world.biome.BiomeGenBase.Height;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;

import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;

public class BiomeGenBuilder {

    private final int id;
    private final BlockMeta stone = new BlockMeta(Blocks.stone, 0);

    String name = "unset";
    Height height = new Height(0, 0);
    float temperature = 0.4F;
    float rainfall = 0.0F;
    boolean generateBedrock = true;
    TerrainConfiguration terrain;
    int snowHeight = 512;
    int oceanHeight = 0;
    int seabedHeight = 0;
    BlockMeta oceanFiller = stone;
    BlockMeta oceanSurface = stone;
    BlockMeta seabed = stone;
    BlockMeta topBlock = stone;
    BlockMeta fillerBlock = stone;
    BlockMeta snowBlock = stone;

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
        return topBlock(new BlockMeta(block, 0));
    }

    public BiomeGenBuilder topBlock(BlockMeta block) {
        this.topBlock = block;
        return this;
    }

    public BiomeGenBuilder fillerBlock(Block block) {
        return fillerBlock(new BlockMeta(block, 0));
    }

    public BiomeGenBuilder fillerBlock(BlockMeta block) {
        this.fillerBlock = block;
        return this;
    }

    public BiomeGenBuilder snowBlock(BlockMeta blockMeta, int snowHeight) {
        this.snowBlock = blockMeta;
        this.snowHeight = snowHeight;
        return this;
    }

    public BiomeGenBuilder ocean(BlockMeta oceanFiller, BlockMeta oceanSurface, int oceanHeight, BlockMeta seabed,
        int seabedHeight) {
        this.oceanFiller = oceanFiller;
        this.oceanSurface = oceanSurface;
        this.oceanHeight = oceanHeight;
        this.seabed = seabed;
        this.seabedHeight = seabedHeight;
        return this;
    }

    public BiomeGenBuilder generateBedrock(boolean generateBedrock) {
        this.generateBedrock = generateBedrock;
        return this;
    }

    public BiomeGenBuilder terrain(TerrainConfiguration terrain) {
        this.terrain = terrain;
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
