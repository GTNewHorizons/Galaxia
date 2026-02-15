package com.gtnewhorizons.galaxia.dimension;

import java.util.function.Supplier;

import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;

public abstract class WorldProviderSpace extends WorldProvider {

    protected boolean hasSky = true;
    protected float cloudHeight = 8.0F;
    protected boolean isSurface = false;
    protected int avgGround = 64;
    protected Vec3 fogColor = Vec3.createVectorHelper(0.2D, 0.1D, 0.4D);
    protected String name;

    protected Supplier<IChunkProvider> chunkGenSupplier;
    protected BiomeGenBase biome;

    protected void applyFlags() {
        this.hasNoSky = !hasSky;
    }

    @Override
    protected void registerWorldChunkManager() {
        this.worldChunkMgr = new WorldChunkManagerHell(biome, 0.8F);
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return chunkGenSupplier != null ? chunkGenSupplier.get()
            : new ChunkProviderGenerate(worldObj, worldObj.getSeed(), false);
    }

    @Override
    public String getDimensionName() {
        return name;
    }
}
