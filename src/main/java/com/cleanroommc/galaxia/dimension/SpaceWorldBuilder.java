package com.cleanroommc.galaxia.dimension;

import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.function.Supplier;

public class SpaceWorldBuilder {

    private final WorldProviderSpace provider;

    private SpaceWorldBuilder(WorldProviderSpace provider) {
        this.provider = provider;
    }

    public static SpaceWorldBuilder configure(WorldProviderSpace provider) {
        return new SpaceWorldBuilder(provider);
    }

    public SpaceWorldBuilder sky(boolean sky) {
        provider.hasSky = sky;
        return this;
    }

    public SpaceWorldBuilder cloudHeight(float height) {
        provider.cloudHeight = height;
        return this;
    }

    public SpaceWorldBuilder surface(boolean surface) {
        provider.isSurface = surface;
        return this;
    }

    public SpaceWorldBuilder avgGround(int level) {
        provider.avgGround = level;
        return this;
    }

    public SpaceWorldBuilder fog(double r, double g, double b) {
        provider.fogColor = Vec3.createVectorHelper(r, g, b);
        return this;
    }

    public SpaceWorldBuilder biome(BiomeGenBase biome) {
        provider.biome = biome;
        return this;
    }

    public SpaceWorldBuilder chunkGen(Supplier<IChunkProvider> gen) {
        provider.chunkGenSupplier = gen;
        return this;
    }

    public void build() {
        provider.applyFlags();
    }
}
