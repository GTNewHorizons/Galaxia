package com.gtnewhorizons.galaxia.registry.dimension.biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase.FlowerEntry;
import net.minecraft.world.biome.BiomeGenBase.Height;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaCave;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaSurface;

/**
 * The builder for biome generation
 */
public class BiomeGenBuilder {

    // Setting basic fields for generation
    private final int id;
    private final Block stone = Blocks.stone;

    String name = "unset";
    Height height = new Height(0, 0);
    float temperature = 0.4F;
    float rainfall = 0.0F;
    TerrainConfiguration terrain;
    int snowHeight = 512;
    int oceanHeight = 0;
    int seabedHeight = 0;
    Block oceanFiller = stone;
    Block oceanSurface = stone;
    Block seabed = stone;
    Block snowBlock = stone;
    List<WorldGenGalaxiaSurface> surfaceFeatures = new ArrayList<>();
    List<WorldGenGalaxiaCave> caveFeatures = new ArrayList<>();
    List<Block> topBlockMetas = new ArrayList<>();
    boolean generateCaves = false;
    int surfaceThickness = 1;
    boolean enableRain = false;
    Block oceanCrackBlock;
    float oceanCrackThickness;
    int oceanCrackComplexity;
    StratificationPreset fillerBlocks;

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
     * Set the top block of the biome (Where the block has meta-data)
     *
     * @param block The required top block (with meta)
     * @return Configured builder
     */
    public BiomeGenBuilder topBlock(Block block) {
        this.topBlockMetas.add(block);
        return this;
    }

    public BiomeGenBuilder fillerBlocks(StratificationPreset fillerBlocks) {
        this.fillerBlocks = fillerBlocks;
        return this;
    }

    /**
     * Sets the snow block for the biome
     *
     * @param blockMeta  The block to be used for snow (with meta)
     * @param snowHeight The height of the snow to generate
     * @return Configured builder
     */
    public BiomeGenBuilder snowBlock(Block blockMeta, int snowHeight) {
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
    public BiomeGenBuilder ocean(Block oceanFiller, Block oceanSurface, int oceanHeight, Block seabed,
        int seabedHeight) {
        this.oceanFiller = oceanFiller;
        this.oceanSurface = oceanSurface;
        this.oceanHeight = oceanHeight;
        this.seabed = seabed;
        this.seabedHeight = seabedHeight;
        return this;
    }

    public BiomeGenBuilder oceanCracks(float oceanCrackThickness, Block oceanCrackBlock, int oceanCrackComplexity) {
        this.oceanCrackThickness = oceanCrackThickness;
        this.oceanCrackBlock = oceanCrackBlock;
        this.oceanCrackComplexity = oceanCrackComplexity;
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

    public BiomeGenBuilder surfaceFeature(WorldGenGalaxiaSurface feature) {
        surfaceFeatures.add(feature);
        return this;
    }

    public BiomeGenBuilder caveFeature(WorldGenGalaxiaCave feature) {
        caveFeatures.add(feature);
        return this;
    }

    public BiomeGenBuilder generateCaves(boolean generateCaves) {
        this.generateCaves = generateCaves;
        return this;
    }

    public BiomeGenBuilder surfaceThickness(int surfaceThickness) {
        this.surfaceThickness = surfaceThickness;
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
