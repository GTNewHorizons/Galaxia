package com.cleanroommc.galaxia.dimension;

import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;

import java.util.function.Supplier;

public abstract class WorldProviderSpace extends WorldProvider {
    protected boolean hasSky = false;
    protected float cloudHeight = 8.0F;
    protected boolean isSurface = false;
    protected int avgGround = 64;
    protected Vec3 fogColor = Vec3.createVectorHelper(0.2D, 0.1D, 0.4D);
    protected Supplier<IChunkProvider> chunkGenSupplier;

    public WorldProviderSpace() {
        this.hasNoSky = !hasSky;
    }

    @Override
    public float getCloudHeight() {
        return cloudHeight;
    }

    @Override
    public boolean isSurfaceWorld() {
        return isSurface;
    }

    @Override
    public int getAverageGroundLevel() {
        return avgGround;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        return true;
    }

    @Override
    public float calculateCelestialAngle(long time, float partial) {
        return 0.0F;
    }

    @Override
    public Vec3 getFogColor(float celestialAngle, float partialTicks) {
        return fogColor;
    }

    @Override
    protected void registerWorldChunkManager() {
        this.worldChunkMgr = new WorldChunkManagerHell(getBiome(), 0.8F);
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        if (chunkGenSupplier != null) {
            return chunkGenSupplier.get();
        }
        return new ChunkProviderGenerate(this.worldObj, this.worldObj.getSeed(), false);
    }

    @Override
    public boolean isSkyColored() {
        return hasSky;
    }

    protected abstract BiomeGenBase getBiome();

    public void setChunkGenSupplier(Supplier<IChunkProvider> supplier) {
        this.chunkGenSupplier = supplier;
    }
}
