package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.worldgen.TerrainPreset;

/**
 * The class holding all data related to the dimension Panspira
 */
public class Panspira extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.PANSPIRA;

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
        return builder.mass(3)
            .orbitalRadius(0.6 * earthRadiusToAU)
            .radius(1.5)
            .gravity(2.25)
            .airResistance(1)
            .effects(
                EffectBuilder.builder()
                    .baseTemp(423)
                    .oxygenPercent(0)
                    .pressure(300)
                    .build());
    }

    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(50)
            // These biome names are mostly just for testing
            .biome(
                createBiome(
                    "Panspira Dunes",
                    GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.SOIL.suffix())
                        .block(),
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
                    GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.STONE.suffix())
                        .block(),
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
                    GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.REGOLITH.suffix())
                        .block(),
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
                    GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.SOIL.suffix())
                        .block(),
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
            .name(ENUM)
            .build();
    }

    protected static BiomeGenBase createBiome(String name, Block block, TerrainConfiguration terrain) {
        return createBiome(name, block, 0, terrain);
    }

    protected static BiomeGenBase createBiome(String name, Block block, int meta, TerrainConfiguration terrain) {
        return new BiomeGenBuilder(100).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(new BlockMeta(block, meta))
            .fillerBlock(GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.STONE.suffix()))
            .snowBlock(GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.SNOW.suffix()), 144)
            .terrain(terrain)
            .ocean(
                new BlockMeta(Blocks.water, 1),
                GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.REGOLITH.suffix()),
                96,
                GalaxiaBlock.get(DimensionEnum.PANSPIRA, BlockVariant.REGOLITH.suffix()),
                64)
            .build();
    }
}
