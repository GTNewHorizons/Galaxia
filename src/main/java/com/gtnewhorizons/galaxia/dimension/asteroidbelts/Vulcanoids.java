package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import net.minecraft.init.Blocks;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.*;
import com.gtnewhorizons.galaxia.structure.Asteroid;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

public class Vulcanoids extends BaseAsteroidBelt {

    public static final PlanetEnum ENUM = PlanetEnum.VULCANOIDS;

    @Override
    protected PlanetEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected Class<? extends WorldProvider> getProviderClass() {
        return WorldProviderVulcanoids.class;
    }

    public static class WorldProviderVulcanoids extends WorldProviderSpace {

        public WorldProviderVulcanoids() {
            Asteroid[] asteroids = new Asteroid[] {
                new Asteroid(
                    12,
                    16,
                    32,
                    new BlockMeta[] { new BlockMeta(Blocks.stone, 0), new BlockMeta(Blocks.cobblestone, 0) },
                    16),
                new Asteroid(
                    16,
                    20,
                    64,
                    new BlockMeta[] { new BlockMeta(Blocks.iron_ore, 0), new BlockMeta(Blocks.iron_block, 0) },
                    2),
                new Asteroid(
                    20,
                    32,
                    128,
                    new BlockMeta[] { new BlockMeta(Blocks.gold_ore, 0), new BlockMeta(Blocks.gold_block, 0) },
                    4),
                new Asteroid(
                    32,
                    128,
                    512,
                    new BlockMeta[]{ new BlockMeta(Blocks.diamond_block, 0), new BlockMeta(Blocks.diamond_ore, 0) },
                    4),
                new Asteroid(
                    32,
                    128,
                    512,
                    new BlockMeta[]{ new BlockMeta(Blocks.stonebrick, 0), new BlockMeta(Blocks.stonebrick, 1) },
                    32)
            };
            WorldProviderBuilder.configure(this)
                .sky(true)
                .skyColor(1, 0.5, 0)
                .fog(0.15f, 0.1f, 0.3f)
                .biome(new BiomeGenVulcanoids(100))
                .name(ENUM)
                .cloudHeight(Integer.MIN_VALUE)
                .chunkGen(() -> new ChunkProviderAsteroidBelt(worldObj, worldObj.getSeed(), asteroids))
                .build();
        }
    }

    public static class BiomeGenVulcanoids extends BiomeGenSpace {

        public BiomeGenVulcanoids(int id) {
            super(
                id,
                new BiomeGenBuilder(id).name("Vulcanoids")
                    .temperature(1.0F)
                    .rainfall(0));
        }
    }
}
