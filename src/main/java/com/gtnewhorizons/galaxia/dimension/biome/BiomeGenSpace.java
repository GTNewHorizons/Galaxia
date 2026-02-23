package com.gtnewhorizons.galaxia.dimension.biome;

import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;

/**
 * The class holding all generation fields for Biome generation
 */
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

    /**
     * Creates a biome generator and configures it based on the provided builder
     *
     * @param id The biome ID
     * @param b  The configured (hopefully) biome builder
     */
    public BiomeGenSpace(int id, BiomeGenBuilder b) {
        super(id);

        // Configure the class based on builder fields
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

        // Set terrain if there is one, if not build a default
        this.terrain = b.terrain != null ? b.terrain
            : TerrainConfiguration.builder()
                .build();
        this.generateBedrock = b.generateBedrock;
    }

    /**
     * Getter for bedrock generation
     *
     * @return boolean - True => generate bedrock
     */
    public boolean generateBedrock() {
        return generateBedrock;
    }

    /**
     * Getter for top block meta
     *
     * @return the top block meta
     */
    public int getTopBlockMeta() {
        return topBlockMeta;
    }

    /**
     * Getter for filler block meta
     *
     * @return the filler block meta
     */
    public int getFillerBlockMeta() {
        return fillerBlockMeta;
    }

    /**
     * Getter for terrain configuration
     *
     * @return the terrain configuration
     */
    public TerrainConfiguration getTerrain() {
        return terrain;
    }

    /**
     * Getter for the snow block
     *
     * @return the snow block
     */
    public BlockMeta getSnowBlock() {
        return snowBlock;
    }

    /**
     * Getter for snow height
     *
     * @return the snow height
     */
    public int getSnowHeight() {
        return snowHeight;
    }

    /**
     * Getter for ocean height
     *
     * @return the ocean height
     */
    public int getOceanHeight() {
        return oceanHeight;
    }

    /**
     * Getter for ocean filler block
     *
     * @return the ocean filler block
     */
    public BlockMeta getOceanFiller() {
        return oceanFiller;
    }

    /**
     * Getter for ocean surface block
     *
     * @return the ocean surface block
     */
    public BlockMeta getOceanSurface() {
        return oceanSurface;
    }

    /**
     * Getter for seabed block
     *
     * @return the seabed block
     */
    public BlockMeta getSeabed() {
        return seabed;
    }

    /**
     * Getter for seabed height
     *
     * @return the seabed height
     */
    public int getSeabedHeight() {
        return seabedHeight;
    }
}
