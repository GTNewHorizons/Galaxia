package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;

/**
 * The class holding all data related to the dimension Panspira
 */
public final class Panspira {

    private Panspira() {}

    public static void configureWorldProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(50)
            // These biome names are mostly just for testing
            .biome(
                createBiome(
                    "Panspira Dunes",
                    PlanetBlocks.PANSPIRA_SOIL,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.SAND_DUNES)
                        .scale(1)
                        .width(1)
                        .height(2)
                        .endFeature()
                        .build()),
                0,
                0)
            .biome(
                createBiome(
                    "Panspira Mountains",
                    PlanetBlocks.PANSPIRA_STONE,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(0.6)
                        .height(4)
                        .width(1)
                        .endFeature()
                        .build()),
                0,
                1)
            .biome(
                createBiome(
                    "Panspira Hills",
                    PlanetBlocks.PANSPIRA_REGOLITH,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(0.125)
                        .height(2)
                        .width(4)
                        .endFeature()
                        .build()),
                1,
                0)
            .biome(
                createBiome(
                    "Panspira Plains",
                    PlanetBlocks.PANSPIRA_SOIL,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(0.5)
                        .height(0.25)
                        .width(2)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .scale(1)
                        .width(5)
                        .height(2)
                        .endFeature()
                        .build()),
                1,
                1)
            .name(DimensionEnum.PANSPIRA)
            .build();
    }

    protected static BiomeGenBase createBiome(String name, Block block, TerrainConfiguration terrain) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(block)
            .fillerBlocks(new StratificationPreset(PlanetBlocks.PANSPIRA_STONE).addStrataLayer(Blocks.bedrock, 0, 0))
            .snowBlock(PlanetBlocks.PANSPIRA_SNOW, 144)
            .terrain(terrain)
            .ocean(Blocks.water, PlanetBlocks.PANSPIRA_REGOLITH, 96, PlanetBlocks.PANSPIRA_REGOLITH, 64)
            .build();
    }
}
