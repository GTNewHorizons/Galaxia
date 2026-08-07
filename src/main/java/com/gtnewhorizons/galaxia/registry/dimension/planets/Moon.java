package com.gtnewhorizons.galaxia.registry.dimension.planets;

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
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.BiomeMatrixGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainModifier;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CrystalClusterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.FluidSpringFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.GeodeFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.StalactiteFeature;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import static com.gtnewhorizons.galaxia.registry.block.PlanetBlocks.*;

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
        BiomeGenBase volcanoes = createOceanBiome(
            "Moon Volcanoes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(32)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .replacementBlock(MOON_MAGMA)
                .width(2)
                .height(32)
                .modifier(TerrainModifier.WEIRDNESS, TerrainModifier.WEIRDNESS.minimum, TerrainModifier.WEIRDNESS.lowerMiddle)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .replacementBlock(MOON_MAGMA)
                .width(4)
                .height(64)
                .modifier(TerrainModifier.WEIRDNESS, TerrainModifier.WEIRDNESS.upperMiddle, TerrainModifier.WEIRDNESS.maximum)
                .endFeature()
                .build());
        BiomeGenBase highlands = createLandBiome(
            "Moon Highlands",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(3)
                .height(24)
                .modifier(TerrainModifier.WEIRDNESS, TerrainModifier.WEIRDNESS.lowerExtreme, TerrainModifier.WEIRDNESS.upperExtreme)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(32)
                .height(192)
                .modifier(TerrainModifier.WEIRDNESS, TerrainModifier.WEIRDNESS.minimum, TerrainModifier.WEIRDNESS.middle)
                .endFeature()
                .feature(TerrainPreset.CANYONS)
                .width(4)
                .height(32)
                .modifier(TerrainModifier.WEIRDNESS, TerrainModifier.WEIRDNESS.middle, TerrainModifier.WEIRDNESS.maximum)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(0.5)
                .height(-16)
                .modifier(TerrainModifier.WEIRDNESS, TerrainModifier.WEIRDNESS.upperExtreme, TerrainModifier.WEIRDNESS.maximum)
                .endFeature()
                .build());
        builder.sky(true)
            .fog(0, 0, 0)
            .skyColor(0, 0, 0.001f)
            .avgGround(80)
            // Biome matrix
            .biomeMatrix(
                new BiomeMatrixGenerator(new String[] { "HHHHHH", "HHHHHH", "HHHbHH", "HHbVbH", "HHHbHH", "HHHHHH" })
                    .addBiomeEntry('H', highlands)
                    .addBiomeEntry('b', border)
                    .addBiomeEntry('V', volcanoes))
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
        BlockMeta anorthosite = new BlockMeta(MOON_DIORITE);

        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(MOON_REGOLITH)
            .fillerBlocks(height -> {
                if (height <= 32) return anorthosite;
                return andesite;
            })
            .caveShape(new CaveShapeCracks())
            .surfaceFeature(
                CraterFeature.builder()
                    .tektite(MOON_TEKTITE)
                    .condition((block, meta) -> block == MOON_REGOLITH || block == MOON_BASALT)
                    .build())
            .undergroundFeature(
                StalactiteFeature.builder()
                    .maxHeight(64)
                    .stalactiteBlock(MOON_DIORITE)
                    .condition((block, meta) -> block == MOON_DIORITE)
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
                    .condition((block, meta) -> block == MOON_DIORITE)
                    .crystalBlock(GalaxiaBlocksEnum.BLOCK_OF_CINNABAR.get())
                    .build())
            .undergroundFeature(
                FluidSpringFeature.builder()
                    .maxHeight(64)
                    .fluid(PlanetBlocks.LIQUID_MERCURY.getBlock())
                    .condition((block, meta) -> block == MOON_ANDESITE || block == MOON_DIORITE)
                    .build())
            .undergroundFeature(
                GeodeFeature.builder()
                    .rarity(32)
                    .minHeight(16)
                    .maxHeight(96)
                    .condition((block, meta) -> block == MOON_DIORITE || block == MOON_ANDESITE)
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

        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(MOON_BASALT)
            .fillerBlocks(height -> {
                if (height <= 32) return gabbro;
                return basalt;
            })
            .caveShape(new CaveShapeTubes((byte) 16, (byte) 4, (short) 100))
            .surfaceFeature(
                CraterFeature.builder()
                    .rarity(64)
                    .tektite(MOON_TEKTITE)
                    .condition((block, meta) -> block == MOON_REGOLITH || block == MOON_BASALT)
                    .build())
            .terrain(terrainConfiguration)
            .ocean(MOON_OBSIDIAN, MOON_BASALT, 56, MOON_OBSIDIAN, 1)
            .oceanCracks(0.3F, MOON_MAGMA, 4)
            .surfaceThickness(4)
            .build();
    }
}
