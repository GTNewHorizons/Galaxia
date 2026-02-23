package com.gtnewhorizons.galaxia.dimension.provider;

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

/**
 * An abstract version of the WorldProvider to be used on Galaxia Planets
 */
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

    /**
     * Applies flags given by builder previously
     */
    protected void applyFlags() {
        this.hasNoSky = !hasSky;
    }

    /**
     * Creates a new provider containing a chunk manager
     */
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

    /**
     * Registers the world chunk manager given the seed
     */
    @Override
    protected void registerWorldChunkManager() {
        ((WorldChunkManagerSpace) this.worldChunkMgr).assignSeed(worldObj.getSeed());
    }

    /**
     * Creates a chunk generator if none existent, or gets current one
     *
     * @return Chunk generator for world provider
     */
    @Override
    public IChunkProvider createChunkGenerator() {
        if (chunkGenSupplier == null) {
            return new ChunkProviderGalaxiaPlanet(worldObj);
        }
        return chunkGenSupplier.get();
    }

    /**
     * Adds a new biome to the matrix
     *
     * @param biome The biome to add
     * @param x     The x index of the matrix to add to
     * @param z     The z index of the matrix to add to
     */
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

    /**
     * Transfers biomes from the world chunk manager to the provider
     */
    public void transferBiomes() {
        ((WorldChunkManagerSpace) worldChunkMgr).provideBiomes(biomes);
    }

    /**
     * Getter for the dimension name
     *
     * @return Dimension name
     */
    @Override
    public String getDimensionName() {
        return name;
    }

    /**
     * Gets the sky colour of the world
     *
     * @param cameraEntity The camera entity to use
     * @param partialTicks The partial ticks (how far through current tick)
     * @return The sky colour as a Vec3
     */
    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getSkyColor(Entity cameraEntity, float partialTicks) {
        if (skyColor != null) return skyColor;
        return super.getSkyColor(cameraEntity, partialTicks);
    }

    /**
     * Gets the sunrise/sunset colours based on celestial angle
     *
     * @param celestialAngle The angle of the main celestial body in the sky
     * @param partialTicks   The partial ticks (how far through current tick)
     * @return The sunrise/sunset colours as a float array
     */
    @Override
    @SideOnly(Side.CLIENT)
    public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
        if (sunriseSunsetColors != null) return sunriseSunsetColors;
        return super.calcSunriseSunsetColors(celestialAngle, partialTicks);
    }

    /**
     * Gets the fog colour based on celestial angle
     *
     * @param celestialAngle The angle of the main celestial body in the sky
     * @param partialTicks   The partial ticks (how far through current tick)
     * @return The fog colour as a Vec3
     */
    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getFogColor(float celestialAngle, float partialTicks) {
        if (fogColor != null) return fogColor;
        return super.getFogColor(celestialAngle, partialTicks);
    }

    /**
     * Gets whether the sky is colored
     *
     * @return Boolean : True => Colored
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean isSkyColored() {
        return skyColored;
    }

    /**
     * Gets whether there are void particles
     *
     * @return Boolean : True => Void particles
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean getWorldHasVoidParticles() {
        return worldHasVoidParticles;
    }

    /**
     * Gets the Void Fog Y Factor
     *
     * @return VoidFogYFactor
     */
    @Override
    @SideOnly(Side.CLIENT)
    public double getVoidFogYFactor() {
        return voidFogYFactor;
    }

    /**
     * Gets whether there is fog shown in X-Z plane based on given coordinates
     *
     * @param x The x coordinate to check
     * @param z The z coordinate to check
     * @return Boolean : True => shows fog
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean doesXZShowFog(int x, int z) {
        return xzShowFog;
    }

    /**
     * Gets the cloud height of the planet
     *
     * @return The cloud height
     */
    @Override
    @SideOnly(Side.CLIENT)
    public float getCloudHeight() {
        return cloudHeight;
    }

    /**
     * Calculates the celestial angle based on world time
     *
     * @param worldTime    The current world time
     * @param partialTicks The partial ticks (how far through current tick)
     * @return The celestial angle
     */
    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        if (celestialCycleSpeed == 1.0F) {
            return super.calculateCelestialAngle(worldTime, partialTicks);
        } else {
            long scaledTime = (long) (worldTime * celestialCycleSpeed);
            return super.calculateCelestialAngle(scaledTime, partialTicks);
        }
    }

    /**
     * Gets the current moon phase based on world time
     *
     * @param worldTime Current world time
     * @return the current moon phase (0 - 7)
     */
    @Override
    public int getMoonPhase(long worldTime) {
        if (moonPhase >= 0 && moonPhase <= 7) return moonPhase;
        return super.getMoonPhase(worldTime);
    }

    /**
     * Gets star brightness
     *
     * @param partialTicks The partial ticks (how far through current tick)
     * @return The star brightness
     */
    @Override
    @SideOnly(Side.CLIENT)
    public float getStarBrightness(float partialTicks) {
        return super.getStarBrightness(partialTicks) * starBrightnessFactor;
    }

    /**
     * Gets sun brightness
     *
     * @param partialTicks The partial ticks (how far through current tick)
     * @return The sun brightness
     */
    @Override
    @SideOnly(Side.CLIENT)
    public float getSunBrightness(float partialTicks) {
        return super.getSunBrightness(partialTicks) * sunBrightnessFactor;
    }

    /**
     * Gets whether the planet is a surface world
     *
     * @return Boolean : True => Surface world
     */
    @Override
    public boolean isSurfaceWorld() {
        return isSurface;
    }

    /**
     * Gets the average ground height
     *
     * @return Average ground level
     */
    @Override
    public int getAverageGroundLevel() {
        return avgGround;
    }

    /**
     * Gets whether compasses etc. should spin wildly
     *
     * @param entity The entity holding the compass etc., playername, or frame-ENTITYID
     * @param x      X Position
     * @param y      Y Position
     * @param z      Z Position
     * @return Boolean : True => Maps/Compasses spin
     */
    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z) {
        return mapSpin;
    }

    /**
     * Gets the dimension to respawn in upon death on this dimension
     *
     * @param player The player that is respawning
     * @return Respawn Dimension ID
     */
    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        return respawnDimension;
    }

    /**
     * Gets whether you can respawn directly on this planet
     *
     * @return Boolean : True => Can respawn here
     */
    @Override
    public boolean canRespawnHere() {
        return canRespawn;
    }

    /**
     * Gets the sky renderer
     *
     * @return The sky renderer being used
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getSkyRenderer() {
        return skyRenderer != null ? skyRenderer : super.getSkyRenderer();
    }

    /**
     * Gets the cloud renderer
     *
     * @return The cloud renderer being used
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getCloudRenderer() {
        return cloudRenderer != null ? cloudRenderer : super.getCloudRenderer();
    }

    /**
     * Gets the weather renderer
     *
     * @return The weather renderer being used
     */
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
