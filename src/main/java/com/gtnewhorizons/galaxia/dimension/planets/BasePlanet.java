package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderSpace;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;

public abstract class BasePlanet {

    public static final double earthRadiusToAU = 23481;

    protected final DimensionDef DEF;

    protected BasePlanet() {
        DEF = createBuilder().build();
    }

    protected DimensionBuilder createBuilder() {
        DimensionEnum planet = getPlanetEnum();

        WorldProviderSpace.registerConfigurator(planet.getId(), this::configureProvider);

        return customizeDimension(
            new DimensionBuilder().enumValue(planet)
                .provider(WorldProviderSpace.class));
    }

    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder;
    }

    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true);
    }

    public DimensionDef getDef() {
        return DEF;
    }

    public abstract DimensionEnum getPlanetEnum();

    protected static BiomeGenBase createBiome(String name, Block block, TerrainConfiguration terrain) {
        return createBiome(name, block, 0, terrain);
    }

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
