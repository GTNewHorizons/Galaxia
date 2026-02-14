package com.cleanroommc.galaxia.world;

import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;

public class WorldProviderCalx extends WorldProvider {

    public WorldProviderCalx() {
        this.hasNoSky = false;
    }

    @Override
    public String getDimensionName() {
        return "Calx (Moon)";
    }

    @Override
    public String getWelcomeMessage() {
        return "§6Entering Calx Orbit...";
    }

    @Override
    public String getDepartMessage() {
        return "§eLeaving Calx";
    }

    @Override
    public float getCloudHeight() {
        return 8.0F;
    }

    @Override
    public boolean isSurfaceWorld() {
        return false;
    }

    @Override
    public int getAverageGroundLevel() {
        return 64;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        return true;
    }

    @Override
    public Vec3 getFogColor(float celestialAngle, float partialTicks) {
        return Vec3.createVectorHelper(0.2D, 0.1D, 0.4D);
    }

    @Override
    protected void registerWorldChunkManager() {
        this.worldChunkMgr = new WorldChunkManagerHell(new BiomeGenCalx(150), 0.8F);
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return new ChunkProviderGenerate(this.worldObj, this.worldObj.getSeed(), false);
    }

    @Override
    public boolean isSkyColored() {
        return true;
    }
}
