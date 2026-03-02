package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.registry.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

/**
 * An abstract class that all planets should derive from
 */
public abstract class BasePlanet {

    // Conversion constant to convert Earth Radii to AU
    public static final double earthRadiusToAU = 23481;

    protected final DimensionDef DEF;

    private static int biomeIdOffset = 0;

    /**
     * Create a dimension def on instantiation of super object
     */
    protected BasePlanet() {
        DEF = createBuilder().build();
    }

    /**
     * Creates a Dimension Builder to add effects and fields to more simply
     *
     * @return The dimension builder configured with the planet enum etc.
     */
    protected DimensionBuilder createBuilder() {
        DimensionEnum planet = getPlanetEnum();

        WorldProviderSpace.registerConfigurator(planet.getId(), this::configureProvider);

        return customizeDimension(
            new DimensionBuilder().enumValue(planet)
                .provider(WorldProviderSpace.class));
    }

    /**
     * The start point of any building chain
     *
     * @param builder The dimension builder to chain on
     * @return The dimension builder ready for chaining
     */
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder;
    }

    /**
     * Configures the WorldProviderBuilder
     *
     * @param builder The world provider builder being configured
     */
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true);
    }

    /**
     * Getter for DimensionDef
     *
     * @return DimensionDef
     */
    public DimensionDef getDef() {
        return DEF;
    }

    /**
     * Abstract method to ensure all planets have a method to get the Enum
     *
     * @return DimensionEnum of planet instance
     */
    public abstract DimensionEnum getPlanetEnum();

    protected static BiomeGenBase createBiome(String name, Block block, TerrainConfiguration terrain,
        boolean generateCaves) {
        return createBiome(name, block, 0, terrain, generateCaves);
    }

    protected static BiomeGenBase createBiome(String name, Block block, TerrainConfiguration terrain) {
        return createBiome(name, block, 0, terrain);
    }

    protected static BiomeGenBase createBiome(String name, Block block, int meta, TerrainConfiguration terrain) {
        return createBiome(name, block, meta, terrain, false);
    }

    protected static BiomeGenBase createBiome(String name, Block block, int meta, TerrainConfiguration terrain,
        boolean generateCaves) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(new BlockMeta(block, meta))
            .topBlock(new BlockMeta(Blocks.wool, 5))
            .fillerBlock(Blocks.brick_block)
            .snowBlock(GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.SNOW.suffix()), 144)
            .terrain(terrain)
            .generateCaves(generateCaves)
            .ocean(
                new BlockMeta(Blocks.glass, 1),
                GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH.suffix()),
                64,
                new BlockMeta(Blocks.obsidian, 0),
                32)
            .build();
    }
}
