package com.gtnewhorizons.galaxia.registry.dimension.planets;

import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_ANDESITE;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_ANORTHOSITE;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_BASALT;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_GABBRO;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_MAGMA;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_OBSIDIAN;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_REGOLITH;
import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.MOON_TEKTITE;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeCracks;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeTubes;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CrystalClusterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.FluidSpringFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.GeodeFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.StalactiteFeature;

/**
 * The class holding all data related to the dimension Moon
 */
public final class Moon {

    private Moon() {}

    /**
     * Configures the world provider to add the correct biomes and settings
     *
     * @param builder The world provider builder being configured
     */
    public static void configureWorldProvider(WorldProviderBuilder builder) {
        BiomeGenBase border = createOceanBiome(
            "Moon Ocean Edge",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(50)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(4)
                .height(32)
                .endFeature()
                .build());
        BiomeGenBase smallVolcanoes = createOceanBiome(
            "Moon Small Volcanoes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(32)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .replacementBlock(MOON_MAGMA)
                .width(2)
                .height(32)
                .endFeature()
                .build());
        BiomeGenBase bigVolcanoes = createOceanBiome(
            "Moon Big Volcanoes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(32)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .replacementBlock(MOON_MAGMA)
                .width(4)
                .height(64)
                .endFeature()
                .build());
        BiomeGenBase hills = createLandBiome(
            "Moon Hills",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(32)
                .height(32)
                .endFeature()
                .feature(TerrainPreset.CANYONS)
                .width(4)
                .height(32)
                .endFeature()
                .build());
        BiomeGenBase mountains = createLandBiome(
            "Moon Mountains",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(3)
                .height(16)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(8)
                .height(128)
                .endFeature()
                .build());
        builder.sky(true)
            .fog(0, 0, 0)
            .skyColor(0, 0, 0.001f)
            .avgGround(80)
            // Inner volcanic biomes
            .biome(smallVolcanoes, 1, 1)
            .biome(bigVolcanoes, 1, 2)
            .biome(bigVolcanoes, 2, 1)
            .biome(smallVolcanoes, 2, 2)
            // Border
            .biome(border, 0, 0)
            .biome(border, 1, 0)
            .biome(border, 2, 0)
            .biome(border, 3, 0)
            .biome(border, 0, 1)
            .biome(border, 3, 1)
            .biome(border, 0, 2)
            .biome(border, 3, 2)
            .biome(border, 0, 3)
            .biome(border, 1, 3)
            .biome(border, 2, 3)
            .biome(border, 3, 3)
            // Hills
            .biome(hills, 4, 0)
            .biome(hills, 5, 0)
            .biome(hills, 6, 0)
            .biome(hills, 7, 0)
            .biome(hills, 4, 1)
            .biome(hills, 7, 1)
            .biome(hills, 4, 2)
            .biome(hills, 7, 2)
            .biome(hills, 4, 3)
            .biome(hills, 5, 3)
            .biome(hills, 6, 3)
            .biome(hills, 7, 3)
            // Mountains
            .biome(mountains, 5, 1)
            .biome(mountains, 5, 2)
            .biome(mountains, 6, 1)
            .biome(mountains, 6, 2)
            // Finish
            .name(DimensionEnum.MOON)
            .build();
    }

    /**
     * Builds a skybox builder with required bodies in the sky
     *
     * @return The SkyBuilder configured with correct bodies
     */
    public static SkyBuilder buildSky() {
        return SkyBuilder.builder()
            .addBody(
                s -> s.texture("minecraft:textures/environment/sun.png")
                    .size(30f)
                    .distance(100.0)
                    .inclination(45)
                    .period(24000L))
            .addBody(
                m -> m.texture("minecraft:textures/environment/moon_phases.png")
                    .size(20f)
                    .distance(-100.0)
                    .inclination(60)
                    .period(23151L)
                    .hasPhases())
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(10.0f)
                    .period(3000L))
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(20.0f)
                    .period(1200L))
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(40.0f)
                    .period(12000L))
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(30.0f)
                    .period(6000L));
    }

    /**
     * Creates a biome generator with specific requirements
     *
     * @return The BiomeGenBase used to generated biomes of that type
     */
    protected static BiomeGenBase createLandBiome(String name, TerrainConfiguration terrainConfiguration) {
        BlockMeta andesite = new BlockMeta(MOON_ANDESITE);
        BlockMeta anorthosite = new BlockMeta(MOON_ANORTHOSITE);
        BlockMeta bedrock = new BlockMeta(Blocks.bedrock);

        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(MOON_REGOLITH)
            .fillerBlocks(height -> {
                if (height == 0) return bedrock;
                if (height <= 32) return anorthosite;
                return andesite;
            })
            .caveShape(new CaveShapeCracks())
            .surfaceFeature(
                CraterFeature.builder()
                    .tektite(MOON_TEKTITE)
                    .condition(
                        (block, meta) -> block == MOON_REGOLITH || block == MOON_BASALT)
                    .build())
            .undergroundFeature(
                StalactiteFeature.builder()
                    .maxHeight(64)
                    .stalactiteBlock(MOON_ANORTHOSITE)
                    .condition((block, meta) -> block == MOON_ANORTHOSITE)
                    .build())
            .undergroundFeature(
                StalactiteFeature.builder()
                    .maxHeight(64)
                    .stalactiteBlock(MOON_ANDESITE)
                    .condition((block, meta) -> block == MOON_ANDESITE)
                    .build())
            .undergroundFeature(
                CrystalClusterFeature.builder()
                    .maxHeight(24)
                    .condition((block, meta) -> block == MOON_ANORTHOSITE)
                    .crystalBlock(GalaxiaBlocksEnum.BLOCK_OF_CINNABAR.get())
                    .build())
            .undergroundFeature(
                FluidSpringFeature.builder()
                    .maxHeight(64)
                    .fluid(PlanetBlocks.LIQUID_MERCURY.getBlock())
                    .condition((block, meta) -> block == MOON_ANDESITE || block == MOON_ANORTHOSITE)
                    .build())
            .undergroundFeature(
                GeodeFeature.builder()
                    .rarity(32)
                    .minHeight(16)
                    .maxHeight(96)
                    .condition((block, meta) -> block == MOON_ANORTHOSITE || block == MOON_ANDESITE)
                    .shell(Blocks.glass)
                    .crystal(Blocks.stained_glass)
                    .build())
            .terrain(terrainConfiguration)
            .ocean(MOON_OBSIDIAN, MOON_BASALT, 1, MOON_OBSIDIAN, 1)
            .surfaceThickness(4)
            .build();
    }

    protected static BiomeGenBase createOceanBiome(String name, TerrainConfiguration terrainConfiguration) {
        BlockMeta basalt = new BlockMeta(MOON_BASALT);
        BlockMeta gabbro = new BlockMeta(MOON_GABBRO);
        BlockMeta bedrock = new BlockMeta(Blocks.bedrock);

        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(MOON_BASALT)
            .fillerBlocks(height -> {
                if (height == 0) return bedrock;
                if (height <= 32) return gabbro;
                return basalt;
            })
            .caveShape(new CaveShapeTubes((byte) 16, (byte) 4, (short) 100))
            .surfaceFeature(
                CraterFeature.builder()
                    .rarity(64)
                    .tektite(MOON_TEKTITE)
                    .condition(
                        (block, meta) -> block == MOON_REGOLITH || block == MOON_BASALT)
                    .build())
            .terrain(terrainConfiguration)
            .ocean(MOON_OBSIDIAN, MOON_BASALT, 56, MOON_OBSIDIAN, 1)
            .oceanCracks(0.3F, MOON_MAGMA, 4)
            .surfaceThickness(4)
            .build();
    }
}
