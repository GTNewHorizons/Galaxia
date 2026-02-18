package com.gtnewhorizons.galaxia.dimension;

import java.util.function.Supplier;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;

import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;

public class WorldProviderBuilder {

    private final WorldProviderSpace provider;
    TerrainConfiguration terrainConfig;

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

    public WorldProviderBuilder terrain(TerrainConfiguration config) {
        this.terrainConfig = config;
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

    public WorldProviderBuilder name(String name) {
        provider.name = name;
        return this;
    }

    public WorldProviderBuilder name(DimensionEnum planet) {
        provider.name = planet.getName();
        return this;
    }

    public WorldProviderBuilder skyColor(double r, double g, double b) {
        provider.skyColor = Vec3.createVectorHelper(r, g, b);
        return this;
    }

    public WorldProviderBuilder sunriseSunsetColors(float r, float g, float b, float a) {
        provider.sunriseSunsetColors = new float[] { r, g, b, a };
        return this;
    }

    public WorldProviderBuilder skyColored(boolean colored) {
        provider.skyColored = colored;
        return this;
    }

    public WorldProviderBuilder worldHasVoidParticles(boolean has) {
        provider.worldHasVoidParticles = has;
        return this;
    }

    public WorldProviderBuilder voidFogYFactor(double factor) {
        provider.voidFogYFactor = factor;
        return this;
    }

    public WorldProviderBuilder xzShowFog(boolean show) {
        provider.xzShowFog = show;
        return this;
    }

    public WorldProviderBuilder starBrightness(float factor) {
        provider.starBrightnessFactor = factor;
        return this;
    }

    public WorldProviderBuilder sunBrightness(float factor) {
        provider.sunBrightnessFactor = factor;
        return this;
    }

    public WorldProviderBuilder celestialCycleSpeed(float speed) {
        provider.celestialCycleSpeed = speed;
        return this;
    }

    public WorldProviderBuilder moonPhase(int phase) {
        if (phase < 0 || phase > 7) throw new IllegalArgumentException("Moon phase must be 0-7");
        provider.moonPhase = phase;
        return this;
    }

    public WorldProviderBuilder movementFactor(double factor) {
        provider.movementFactor = factor;
        return this;
    }

    public WorldProviderBuilder mapSpin(boolean spin) {
        provider.mapSpin = spin;
        return this;
    }

    public WorldProviderBuilder respawnDimension(int dim) {
        provider.respawnDimension = dim;
        return this;
    }

    public WorldProviderBuilder canRespawn(boolean can) {
        provider.canRespawn = can;
        return this;
    }

    public WorldProviderBuilder entrancePortal(int x, int y, int z) {
        provider.entrancePortal = new ChunkCoordinates(x, y, z);
        return this;
    }

    public WorldProviderBuilder skyRenderer(IRenderHandler renderer) {
        provider.skyRenderer = renderer;
        return this;
    }

    public WorldProviderBuilder cloudRenderer(IRenderHandler renderer) {
        provider.cloudRenderer = renderer;
        return this;
    }

    public WorldProviderBuilder weatherRenderer(IRenderHandler renderer) {
        provider.weatherRenderer = renderer;
        return this;
    }

    public void build() {
        provider.terrainConfig = this.terrainConfig;
        provider.applyFlags();
    }
}
