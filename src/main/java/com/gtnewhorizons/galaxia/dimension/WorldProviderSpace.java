package com.gtnewhorizons.galaxia.dimension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;

import com.gtnewhorizons.galaxia.worldgen.ChunkProviderGalaxiaPlanet;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class WorldProviderSpace extends WorldProvider {

    private static final Map<Integer, Consumer<WorldProviderBuilder>> CONFIGS = new ConcurrentHashMap<>();

    private BiomeGenBase[][] biomes;

    protected boolean hasSky = true;
    protected float cloudHeight = 8.0F;
    protected boolean isSurface = true;
    protected int avgGround = 64;
    protected Vec3 fogColor = Vec3.createVectorHelper(0.2D, 0.1D, 0.4D);
    protected String name;

    protected Supplier<IChunkProvider> chunkGenSupplier;
    protected Vec3 skyColor;
    protected float[] sunriseSunsetColors;
    protected boolean skyColored = true;
    protected boolean worldHasVoidParticles = true;
    protected double voidFogYFactor = 0.03125;
    protected boolean xzShowFog = false;
    protected float starBrightnessFactor = 1.0F;
    protected float sunBrightnessFactor = 1.0F;
    protected float celestialCycleSpeed = 1.0F;
    protected int moonPhase; // -1 for time
    protected double movementFactor = 1.0;
    protected boolean mapSpin = false;
    protected int respawnDimension = 0;
    protected boolean canRespawn = false;
    protected ChunkCoordinates entrancePortal;
    protected IRenderHandler skyRenderer;
    protected IRenderHandler cloudRenderer;
    protected IRenderHandler weatherRenderer;

    protected void applyFlags() {
        this.hasNoSky = !hasSky;
    }

    public WorldProviderSpace() {
        super();
        worldChunkMgr = new WorldChunkManagerSpace();
    }

    public static void registerConfigurator(int dimensionId, Consumer<WorldProviderBuilder> configurator) {
        CONFIGS.put(dimensionId, configurator);
    }

    @Override
    public void setDimension(int dimensionId) {
        super.setDimension(dimensionId);

        Consumer<WorldProviderBuilder> config = CONFIGS.get(dimensionId);
        if (config != null) {
            WorldProviderBuilder builder = WorldProviderBuilder.configure(this);
            config.accept(builder);
            builder.build();
        }
    }

    @Override
    protected void registerWorldChunkManager() {
        ((WorldChunkManagerSpace) this.worldChunkMgr).assignSeed(worldObj.getSeed());
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        if (chunkGenSupplier == null) {
            return new ChunkProviderGalaxiaPlanet(worldObj);
        }
        return chunkGenSupplier.get();
    }

    public void addBiome(BiomeGenBase biome, int x, int z) {
        if (biomes == null) {
            biomes = new BiomeGenBase[x + 1][z + 1];
        } else if (x >= biomes.length || z >= biomes[0].length) {
            BiomeGenBase[][] biggerMatrix = new BiomeGenBase[Math.max(x + 1, biomes.length)][Math
                .max(z + 1, biomes[0].length)];
            for (int oldX = 0; oldX < biomes.length; oldX++) {
                System.arraycopy(biomes[oldX], 0, biggerMatrix[oldX], 0, biomes[0].length);
            }
            biomes = biggerMatrix;
        }
        biomes[x][z] = biome;
    }

    public void transferBiomes() {
        ((WorldChunkManagerSpace) worldChunkMgr).provideBiomes(biomes);
    }

    @Override
    public String getDimensionName() {
        return name;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getSkyColor(Entity cameraEntity, float partialTicks) {
        if (skyColor != null) return skyColor;
        return super.getSkyColor(cameraEntity, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
        if (sunriseSunsetColors != null) return sunriseSunsetColors;
        return super.calcSunriseSunsetColors(celestialAngle, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getFogColor(float celestialAngle, float partialTicks) {
        if (fogColor != null) return fogColor;
        return super.getFogColor(celestialAngle, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isSkyColored() {
        return skyColored;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean getWorldHasVoidParticles() {
        return worldHasVoidParticles;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getVoidFogYFactor() {
        return voidFogYFactor;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean doesXZShowFog(int x, int z) {
        return xzShowFog;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getCloudHeight() {
        return cloudHeight;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        if (celestialCycleSpeed == 1.0F) {
            return super.calculateCelestialAngle(worldTime, partialTicks);
        } else {
            long scaledTime = (long) (worldTime * celestialCycleSpeed);
            return super.calculateCelestialAngle(scaledTime, partialTicks);
        }
    }

    @Override
    public int getMoonPhase(long worldTime) {
        if (moonPhase >= 0 && moonPhase <= 7) return moonPhase;
        return super.getMoonPhase(worldTime);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getStarBrightness(float partialTicks) {
        return super.getStarBrightness(partialTicks) * starBrightnessFactor;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getSunBrightness(float partialTicks) {
        return super.getSunBrightness(partialTicks) * sunBrightnessFactor;
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
    public double getMovementFactor() {
        return movementFactor;
    }

    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z) {
        return mapSpin;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        return respawnDimension;
    }

    @Override
    public boolean canRespawnHere() {
        return canRespawn;
    }

    @Override
    public ChunkCoordinates getEntrancePortalLocation() {
        return entrancePortal;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getSkyRenderer() {
        return skyRenderer != null ? skyRenderer : super.getSkyRenderer();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getCloudRenderer() {
        return cloudRenderer != null ? cloudRenderer : super.getCloudRenderer();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getWeatherRenderer() {
        return weatherRenderer != null ? weatherRenderer : super.getWeatherRenderer();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setSkyRenderer(IRenderHandler skyRenderer) {
        this.skyRenderer = skyRenderer;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setCloudRenderer(IRenderHandler cloudRenderer) {
        this.cloudRenderer = cloudRenderer;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setWeatherRenderer(IRenderHandler weatherRenderer) {
        this.weatherRenderer = weatherRenderer;
    }
}
