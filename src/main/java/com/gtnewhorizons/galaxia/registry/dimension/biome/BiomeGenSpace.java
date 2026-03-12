package com.gtnewhorizons.galaxia.registry.dimension.biome;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaCave;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaSurface;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaWall;

/**
 * The class holding all generation fields for Biome generation
 */
public class BiomeGenSpace extends BiomeGenBase {

    private final List<Block> topBlockMetas;
    private final TerrainConfiguration terrain;
    private final int snowHeight;
    private final Block snowBlock;
    private final int oceanHeight;
    private final int seabedHeight;
    private final Block oceanFiller;
    private final Block oceanSurface;
    private final Block seabed;
    private final List<WorldGenGalaxiaSurface> surfaceFeatures;
    private final List<WorldGenGalaxiaCave> caveFeatures;
    private final List<WorldGenGalaxiaWall> wallFeatures;
    private final boolean generateCaves;
    private final int surfaceThickness;
    private final Block oceanCrackBlock;
    private final float oceanCrackThickness;
    private final int oceanCrackComplexity;
    private final StratificationPreset fillerBlocks;

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
        this.enableRain = b.enableRain;

        this.fillerBlocks = b.fillerBlocks;
        this.topBlockMetas = b.topBlockMetas;
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
        this.surfaceFeatures = b.surfaceFeatures;
        this.caveFeatures = b.caveFeatures;
        this.wallFeatures = b.wallFeatures;
        this.generateCaves = b.generateCaves;
        this.surfaceThickness = b.surfaceThickness;
        this.oceanCrackThickness = b.oceanCrackThickness;
        this.oceanCrackBlock = b.oceanCrackBlock;
        this.oceanCrackComplexity = b.oceanCrackComplexity;

        // Set terrain if there is one, if not build a default
        this.terrain = b.terrain != null ? b.terrain
            : TerrainConfiguration.builder()
                .build();
    }

    public StratificationPreset getFillerBlocks() {
        return fillerBlocks;
    }

    /**
     * Getter for top block meta
     *
     * @return the top block meta
     */
    public List<Block> getTopBlockMetas() {
        return topBlockMetas;
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
    public Block getSnowBlock() {
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
    public Block getOceanFiller() {
        return oceanFiller;
    }

    /**
     * Getter for ocean surface block
     *
     * @return the ocean surface block
     */
    public Block getOceanSurface() {
        return oceanSurface;
    }

    /**
     * Getter for seabed block
     *
     * @return the seabed block
     */
    public Block getSeabed() {
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

    public int getSurfaceThickness() {
        return surfaceThickness;
    }

    public List<WorldGenGalaxiaSurface> getSurfaceFeatures() {
        return surfaceFeatures;
    }

    public List<WorldGenGalaxiaCave> getCaveFeatures() {
        return caveFeatures;
    }

    public List<WorldGenGalaxiaWall> getWallFeatures() {
        return wallFeatures;
    }

    public boolean generateCaves() {
        return generateCaves;
    }

    public Block getOceanCrackBlock() {
        return oceanCrackBlock;
    }

    public float getOceanCrackThickness() {
        return oceanCrackThickness;
    }

    public int getOceanCrackComplexity() {
        return oceanCrackComplexity;
    }
}
