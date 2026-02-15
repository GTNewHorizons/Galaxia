package com.cleanroommc.galaxia.dimension;

import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.function.Supplier;

public class WorldProviderBuilder {

    private final WorldProviderSpace provider;

    private WorldProviderBuilder(WorldProviderSpace provider) {
        this.provider = provider;
    }

    public static WorldProviderBuilder configure(WorldProviderSpace provider) {
        return new WorldProviderBuilder(provider);
    }

    public WorldProviderBuilder sky(boolean sky) {
        provider.hasSky = sky;
        return this;
    }

    public WorldProviderBuilder cloudHeight(float height) {
        provider.cloudHeight = height;
        return this;
    }

    public WorldProviderBuilder surface(boolean surface) {
        provider.isSurface = surface;
        return this;
    }

    public WorldProviderBuilder avgGround(int level) {
        provider.avgGround = level;
        return this;
    }

    public WorldProviderBuilder fog(double r, double g, double b) {
        provider.fogColor = Vec3.createVectorHelper(r, g, b);
        return this;
    }

    public WorldProviderBuilder biome(BiomeGenBase biome) {
        provider.biome = biome;
        return this;
    }

    public WorldProviderBuilder chunkGen(Supplier<IChunkProvider> gen) {
        provider.chunkGenSupplier = gen;
        return this;
    }

    public void build() {
        provider.applyFlags();
    }
}
