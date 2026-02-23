package com.gtnewhorizons.galaxia.dimension.biome;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase.FlowerEntry;
import net.minecraft.world.biome.BiomeGenBase.Height;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;

import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;

/**
 * The builder for biome generation
 */
public class BiomeGenBuilder {

    // Setting basic fields for generation
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

    /**
     * Instantiates a builder for a given biome ID
     *
     * @param id The biome ID
     */
    public BiomeGenBuilder(int id) {
        this.id = id;
    }

    /**
     * Set name of biome in builder
     *
     * @param name The required biome name
     * @return Configured builder
     */
    public BiomeGenBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the height of the biome
     *
     * @param low  The lowest point of the biome to generate
     * @param high The highest point of the biome to generate
     * @return Configured builder
     */
    public BiomeGenBuilder height(float low, float high) {
        this.height = new Height(low, high);
        return this;
    }

    /**
     * Set the temperature of the biome
     *
     * @param temp The required temperature of the biome
     * @return Configured builder
     */
    public BiomeGenBuilder temperature(float temp) {
        this.temperature = temp;
        return this;
    }

    /**
     * Set the rainfall of the biome
     *
     * @param rain The required rainfall
     * @return Configured builder
     */
    public BiomeGenBuilder rainfall(float rain) {
        this.rainfall = rain;
        return this;
    }

    /**
     * Set the top block of the biome
     *
     * @param block The required top block
     * @return Configured builder
     */
    public BiomeGenBuilder topBlock(Block block) {
        return topBlock(new BlockMeta(block, 0));
    }

    /**
     * Set the top block of the biome (Where the block has meta-data)
     *
     * @param block The required top block (with meta)
     * @return Configured builder
     */
    public BiomeGenBuilder topBlock(BlockMeta block) {
        this.topBlock = block;
        return this;
    }

    /**
     * Set the filler block for the biome
     *
     * @param block The required filler block
     * @return Configured builder
     */
    public BiomeGenBuilder fillerBlock(Block block) {
        return fillerBlock(new BlockMeta(block, 0));
    }

    /**
     * Set the filler block for the biome (Where the block has meta-data)
     *
     * @param block The required filler block (with meta)
     * @return Configured builder
     */
    public BiomeGenBuilder fillerBlock(BlockMeta block) {
        this.fillerBlock = block;
        return this;
    }

    /**
     * Sets the snow block for the biome
     *
     * @param blockMeta  The block to be used for snow (with meta)
     * @param snowHeight The height of the snow to generate
     * @return Configured builder
     */
    public BiomeGenBuilder snowBlock(BlockMeta blockMeta, int snowHeight) {
        this.snowBlock = blockMeta;
        this.snowHeight = snowHeight;
        return this;
    }

    /**
     * Sets the ocean for the bioome
     *
     * @param oceanFiller  The filler block for the ocean to use (with meta)
     * @param oceanSurface The surface block of the ocean (water, ice layer etc.) [with meta]
     * @param oceanHeight  The height of the ocean (sea-level)
     * @param seabed       The block to use for the seabed (with meta)
     * @param seabedHeight The height of the seabed
     * @return Configured builder
     */
    public BiomeGenBuilder ocean(BlockMeta oceanFiller, BlockMeta oceanSurface, int oceanHeight, BlockMeta seabed,
        int seabedHeight) {
        this.oceanFiller = oceanFiller;
        this.oceanSurface = oceanSurface;
        this.oceanHeight = oceanHeight;
        this.seabed = seabed;
        this.seabedHeight = seabedHeight;
        return this;
    }

    /**
     * Sets whether to generate bedrock
     *
     * @param generateBedrock True = bedrock layer
     * @return Configured builder
     */
    public BiomeGenBuilder generateBedrock(boolean generateBedrock) {
        this.generateBedrock = generateBedrock;
        return this;
    }

    /**
     * Sets the required terrain configuration for the biome
     *
     * @param terrain The terrain configuration to use in the biome
     * @return Configured builder
     */
    public BiomeGenBuilder terrain(TerrainConfiguration terrain) {
        this.terrain = terrain;
        return this;
    }

    /**
     * Sets the mobs able to spawn in this biome
     *
     * @param list The list of possible spawns
     * @return Configured builder
     */
    public BiomeGenBuilder mobsAll(List<SpawnListEntry> list) {
        this.mobsGeneral = list;
        this.mobsMonster = list;
        this.mobsWater = list;
        this.mobsCave = list;
        return this;
    }

    /**
     * Sets the general (passive) mobs that can spawn in this biome
     *
     * @param list The list of general mobs that can spawn
     * @return Configured builder
     */
    public BiomeGenBuilder mobsGeneral(List<SpawnListEntry> list) {
        this.mobsGeneral = list;
        return this;
    }

    /**
     * Sets the monster (hostile mobs) mobs that can spawn in this biome
     *
     * @param list The list of monster mobs that can spawn
     * @return Configured builder
     */
    public BiomeGenBuilder mobsMonster(List<SpawnListEntry> list) {
        this.mobsMonster = list;
        return this;
    }

    /**
     * Sets the water (squids etc.) mobs that can spawn in this biome
     *
     * @param list The list of water mobs that can spawn
     * @return Configured builder
     */
    public BiomeGenBuilder mobsWater(List<SpawnListEntry> list) {
        this.mobsWater = list;
        return this;
    }

    /**
     * Sets the cave (bats etc.) mobs that can spawn in this biome
     *
     * @param list The list of cave mobs that can spawn
     * @return Configured builder
     */
    public BiomeGenBuilder mobsCave(List<SpawnListEntry> list) {
        this.mobsCave = list;
        return this;
    }

    /**
     * Sets the flowers that can spawn in this biome
     *
     * @param list The list of flowers that can spawn
     * @return Configured builder
     */
    public BiomeGenBuilder flowers(List<FlowerEntry> list) {
        this.flowers = list;
        return this;
    }

    /**
     * Builds the BiomeGenSpace based on given fields
     *
     * @return BiomeGenSpace configured from previous chained methods
     */
    public BiomeGenSpace build() {
        return new BiomeGenSpace(id, this);
    }
}
