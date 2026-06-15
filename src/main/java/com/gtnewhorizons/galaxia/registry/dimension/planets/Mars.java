package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShape;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeCracks;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationLayers;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;

/**
 * The class holding all data related to the dimension Mars
 */
public final class Mars {

    private Mars() {}

    /**
     * Configures the world provider to add the correct biomes and settings
     *
     * @param builder The world provider builder being configured
     */
    public static void configureWorldProvider(WorldProviderBuilder builder) {
        CaveShape marsCaves = new CaveShapeCracks();
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .biome(
                createBiome(
                    "Mars Dunes",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .height(16)
                        .width(1.5)
                        .endFeature()
                        .build(),
                    marsCaves,
                    PlanetBlocks.MARS_REGOLITH),
                0,
                0)
            .biome(
                createBiome(
                    "Mars Mountains",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .height(64)
                        .width(2)
                        .endFeature()
                        .build(),
                    null,
                    PlanetBlocks.MARS_REGOLITH),
                0,
                1)
            .biome(
                createBiome(
                    "Mars Flatlands",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .width(0.5)
                        .height(6)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .height(8)
                        .width(2)
                        .endFeature()
                        .build(),
                    marsCaves,
                    PlanetBlocks.MARS_REGOLITH),
                1,
                0)
            .biome(
                createBiome(
                    "Mars Basins",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(16)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .height(8)
                        .width(0.5)
                        .endFeature()
                        .build(),
                    null,
                    PlanetBlocks.MARS_RHYOLITE),
                1,
                1)
            .name(DimensionEnum.MARS)
            .build();
    }

    protected static BiomeGenBase createBiome(String name, TerrainConfiguration terrain, CaveShape caveShape,
        Block surfaceBlock) {
        BlockMeta andesite = new BlockMeta(PlanetBlocks.MARS_ANDESITE);
        BlockMeta anorthosite = new BlockMeta(PlanetBlocks.MARS_ANORTHOSITE);
        BlockMeta bedrock = new BlockMeta(Blocks.bedrock);

        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(surfaceBlock)
            .fillerBlocks(height -> {
                if (height == 0) return bedrock;
                if (height <= 32) return anorthosite;
                return andesite;
            })
            .snowBlock(PlanetBlocks.MARS_SNOW, 144)
            .terrain(terrain)
            .caveShape(caveShape)
            .build();
    }
}
