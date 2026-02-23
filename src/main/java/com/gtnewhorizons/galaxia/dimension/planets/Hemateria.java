package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.worldgen.TerrainPreset;

/**
 * The class holding all data related to the dimension Hemateria
 */
public class Hemateria extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.HEMATERIA;

    /**
     * Getter for dimension Enum
     * 
     * @return Dimension Enum
     */
    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    /**
     * The configuration of the DimensionBuilder to configure the dimension
     * 
     * @param builder The dimension builder to chain on
     * @return The dimension Builder with all properties assigned
     */
    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.mass(0.1)
            .orbitalRadius(1.52 * earthRadiusToAU)
            .radius(0.53)
            .gravity(0.5)
            .airResistance(0.7)
            .effects(
                new EffectBuilder().baseTemp(67)
                    .oxygenPercent(0)
                    .pressure(1));
    }

    /**
     * Configures the world provider to add the correct biomes and settings
     * 
     * @param builder The world provider builder being configured
     */
    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(80)
            // These biome names are mostly just for testing
            .createBiomeMatrix(2)
            .biome(
                createBiome(
                    "Hemateria Dunes",
                    Blocks.brick_block,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.SAND_DUNES)
                        .scale(4)
                        .width(1.5)
                        .height(2)
                        .endFeature()
                        .build()),
                0,
                0)
            .biome(
                createBiome(
                    "Hemateria Mountains",
                    Blocks.wool,
                    4,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(4)
                        .height(0.5)
                        .width(2)
                        .endFeature()
                        .build()),
                0,
                1)
            .biome(
                createBiome(
                    "Hemateria Hills",
                    GalaxiaBlockBase.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH.suffix)
                        .block(),
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(0.25)
                        .height(4)
                        .width(2)
                        .endFeature()
                        .build()),
                1,
                0)
            .biome(
                createBiome(
                    "Hemateria Dune Hills",
                    GalaxiaBlockBase.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix)
                        .block(),
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(4)
                        .height(0.5)
                        .width(2)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .scale(4)
                        .width(1.5)
                        .height(2)
                        .endFeature()
                        .build()),
                1,
                1)
            .name(ENUM)
            .build();
    }

    /**
     * Creates a biome generator with a specific block type, and terrain configuration
     * 
     * @param name    Biome name
     * @param block   The block used for the biome top block
     * @param terrain The required terrain configuration
     * @return The BiomeGenBase used to generated biomes of that type
     */
    protected static BiomeGenBase createBiome(String name, Block block, TerrainConfiguration terrain) {
        return createBiome(name, block, 0, terrain);
    }

    /**
     * Creates a biome with a specific block type (containing meta), and terrain configuration
     * 
     * @param name    The biome name
     * @param block   The block used for the top block
     * @param meta    The meta-data of the block
     * @param terrain The required terrain configuration
     * @return
     */
    protected static BiomeGenBase createBiome(String name, Block block, int meta, TerrainConfiguration terrain) {
        return new BiomeGenBuilder(100).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(new BlockMeta(block, meta))
            .fillerBlock(Blocks.brick_block)
            .snowBlock(GalaxiaBlockBase.get(DimensionEnum.HEMATERIA, BlockVariant.SNOW.suffix), 144)
            .terrain(terrain)
            .ocean(
                new BlockMeta(Blocks.glass, 1),
                GalaxiaBlockBase.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH.suffix),
                64,
                new BlockMeta(Blocks.obsidian, 0),
                32)
            .build();
    }
}
