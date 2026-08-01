package com.gtnewhorizons.galaxia.registry.dimension.planets;

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
        BiomeGenBase flatlands = createBiome(
            "Mars Flatlands",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(8)
                .width(2)
                .endFeature()
                .build(),
            marsCaves
        );
        BiomeGenBase dunes = createBiome(
            "Mars Dunes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.SAND_DUNES)
                .height(16)
                .width(1.5)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(8)
                .width(2)
                .endFeature()
                .build(),
            marsCaves
        );
        BiomeGenBase slopes = createBiome(
            "Mars Slopes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(32)
                .width(2)
                .endFeature()
                .build(),
            null
        );
        BiomeGenBase hills = createBiome(
            "Mars Hills",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(64)
                .width(4)
                .endFeature()
                .build(),
            null
        );
        BiomeGenBase mountains = createBiome(
            "Mars Mountains",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(256)
                .width(6)
                .endFeature()
                .build(),
            null
        );
        BiomeGenBase supermountains = createBiome(
            "Mars Supermountains",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(1024)
                .width(12)
                .endFeature()
                .build(),
            null
        );
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            // Layer 0
            .biome(flatlands, 0, 0)
            .biome(flatlands, 1, 0)
            .biome(flatlands, 2, 0)
            .biome(flatlands, 3, 0)
            .biome(flatlands, 4, 0)
            .biome(flatlands, 5, 0)
            .biome(flatlands, 6, 0)
            .biome(flatlands, 7, 0)
            .biome(flatlands, 8, 0)
            .biome(flatlands, 9, 0)
            .biome(flatlands, 10, 0)
            .biome(flatlands, 11, 0)
            // Layer 1
            .biome(flatlands, 0, 1)
            .biome(dunes, 1, 1)
            .biome(dunes, 2, 1)
            .biome(dunes, 3, 1)
            .biome(dunes, 4, 1)
            .biome(dunes, 5, 1)
            .biome(dunes, 6, 1)
            .biome(dunes, 7, 1)
            .biome(dunes, 8, 1)
            .biome(dunes, 9, 1)
            .biome(dunes, 10, 1)
            .biome(flatlands, 11, 1)
            // Layer 2
            .biome(flatlands, 0, 2)
            .biome(dunes, 1, 2)
            .biome(slopes, 2, 2)
            .biome(slopes, 3, 2)
            .biome(slopes, 4, 2)
            .biome(slopes, 5, 2)
            .biome(slopes, 6, 2)
            .biome(slopes, 7, 2)
            .biome(slopes, 8, 2)
            .biome(slopes, 9, 2)
            .biome(dunes, 10, 2)
            .biome(flatlands, 11, 2)
            // Layer 3
            .biome(flatlands, 0, 3)
            .biome(dunes, 1, 3)
            .biome(slopes, 2, 3)
            .biome(hills, 3, 3)
            .biome(hills, 4, 3)
            .biome(hills, 5, 3)
            .biome(hills, 6, 3)
            .biome(hills, 7, 3)
            .biome(hills, 8, 3)
            .biome(slopes, 9, 3)
            .biome(dunes, 10, 3)
            .biome(flatlands, 11, 3)
            // Layer 4
            .biome(flatlands, 0, 4)
            .biome(dunes, 1, 4)
            .biome(slopes, 2, 4)
            .biome(hills, 3, 4)
            .biome(mountains, 4, 4)
            .biome(mountains, 5, 4)
            .biome(mountains, 6, 4)
            .biome(mountains, 7, 4)
            .biome(hills, 8, 4)
            .biome(slopes, 9, 4)
            .biome(dunes, 10, 4)
            .biome(flatlands, 11, 4)
            // Layer 5
            .biome(flatlands, 0, 5)
            .biome(dunes, 1, 5)
            .biome(slopes, 2, 5)
            .biome(hills, 3, 5)
            .biome(mountains, 4, 5)
            .biome(supermountains, 5, 5)
            .biome(supermountains, 6, 5)
            .biome(mountains, 7, 5)
            .biome(hills, 8, 5)
            .biome(slopes, 9, 5)
            .biome(dunes, 10, 5)
            .biome(flatlands, 11, 5)
            // Layer 6
            .biome(flatlands, 0, 6)
            .biome(dunes, 1, 6)
            .biome(slopes, 2, 6)
            .biome(hills, 3, 6)
            .biome(mountains, 4, 6)
            .biome(supermountains, 5, 6)
            .biome(supermountains, 6, 6)
            .biome(mountains, 7, 6)
            .biome(hills, 8, 6)
            .biome(slopes, 9, 6)
            .biome(dunes, 10, 6)
            .biome(flatlands, 11, 6)
            // Layer 7
            .biome(flatlands, 0, 7)
            .biome(dunes, 1, 7)
            .biome(slopes, 2, 7)
            .biome(hills, 3, 7)
            .biome(mountains, 4, 7)
            .biome(mountains, 5, 7)
            .biome(mountains, 6, 7)
            .biome(mountains, 7, 7)
            .biome(hills, 8, 7)
            .biome(slopes, 9, 7)
            .biome(dunes, 10, 7)
            .biome(flatlands, 11, 7)
            // Layer 8
            .biome(flatlands, 0, 8)
            .biome(dunes, 1, 8)
            .biome(slopes, 2, 8)
            .biome(hills, 3, 8)
            .biome(hills, 4, 8)
            .biome(hills, 5, 8)
            .biome(hills, 6, 8)
            .biome(hills, 7, 8)
            .biome(hills, 8, 8)
            .biome(slopes, 9, 8)
            .biome(dunes, 10, 8)
            .biome(flatlands, 11, 8)
            // Layer 9
            .biome(flatlands, 0, 9)
            .biome(dunes, 1, 9)
            .biome(slopes, 2, 9)
            .biome(slopes, 3, 9)
            .biome(slopes, 4, 9)
            .biome(slopes, 5, 9)
            .biome(slopes, 6, 9)
            .biome(slopes, 7, 9)
            .biome(slopes, 8, 9)
            .biome(slopes, 9, 9)
            .biome(dunes, 10, 9)
            .biome(flatlands, 11, 9)
            // Layer 10
            .biome(flatlands, 0, 10)
            .biome(dunes, 1, 10)
            .biome(slopes, 2, 10)
            .biome(slopes, 3, 10)
            .biome(slopes, 4, 10)
            .biome(slopes, 5, 10)
            .biome(slopes, 6, 10)
            .biome(slopes, 7, 10)
            .biome(slopes, 8, 10)
            .biome(slopes, 9, 10)
            .biome(dunes, 10, 10)
            .biome(flatlands, 11, 10)
            // Layer 11
            .biome(flatlands, 0, 11)
            .biome(flatlands, 1, 11)
            .biome(flatlands, 2, 11)
            .biome(flatlands, 3, 11)
            .biome(flatlands, 4, 11)
            .biome(flatlands, 5, 11)
            .biome(flatlands, 6, 11)
            .biome(flatlands, 7, 11)
            .biome(flatlands, 8, 11)
            .biome(flatlands, 9, 11)
            .biome(flatlands, 10, 11)
            .biome(flatlands, 11, 11)
            .name(DimensionEnum.MARS)
            .build();
    }

    protected static BiomeGenBase createBiome(String name, TerrainConfiguration terrain, CaveShape caveShape) {
        BlockMeta andesite = new BlockMeta(PlanetBlocks.MARS_ANDESITE);
        BlockMeta anorthosite = new BlockMeta(PlanetBlocks.MARS_ANORTHOSITE);
        BlockMeta bedrock = new BlockMeta(Blocks.bedrock);

        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(PlanetBlocks.MARS_REGOLITH)
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
