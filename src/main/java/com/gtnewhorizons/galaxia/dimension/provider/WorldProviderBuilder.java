package com.gtnewhorizons.galaxia.dimension.provider;

import java.util.function.Supplier;

import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;

/**
 * A builder class to generate a world provider with configuration
 */
public class WorldProviderBuilder {

    private final WorldProviderSpace provider;

    private WorldProviderBuilder(WorldProviderSpace provider) {
        this.provider = provider;
    }

    /**
     * The first method to be called in a configuration chain
     *
     * @param provider The world provider to configure
     * @return Builder object
     */
    public static WorldProviderBuilder configure(WorldProviderSpace provider) {
        return new WorldProviderBuilder(provider);
    }

    /**
     * Sets whether the provider should have a sky
     *
     * @param sky Boolean : True => has a sky
     * @return Configured builder
     */
    public WorldProviderBuilder sky(boolean sky) {
        provider.hasSky = sky;
        return this;
    }

    /**
     * Sets the cloud height of the provider
     *
     * @param height The required cloud height
     * @return Configured builder
     */
    public WorldProviderBuilder cloudHeight(float height) {
        provider.cloudHeight = height;
        return this;
    }

    /**
     * Sets whether the world provider should have a surface (planets vs. asteroid belts etc.)
     *
     * @param surface Boolean : True => Has a surface
     * @return Configured builder
     */
    public WorldProviderBuilder surface(boolean surface) {
        provider.isSurface = surface;
        return this;
    }

    /**
     * Sets the average ground height of the world provider
     *
     * @param level The required average ground height
     * @return Configured builder
     */
    public WorldProviderBuilder avgGround(int level) {
        provider.avgGround = level;
        return this;
    }

    /**
     * Sets the colour of the fog required in the world provider (RGB format)
     *
     * @param r The amount of red
     * @param g The amount of green
     * @param b The amount of blue
     * @return Configured builder
     */
    public WorldProviderBuilder fog(double r, double g, double b) {
        provider.fogColor = Vec3.createVectorHelper(r, g, b);
        return this;
    }

    /**
     * Adds a biome to the provider
     *
     * @param biome The BiomeGenSpace to add
     * @param x     The x index of the biome in the matrix
     * @param z     The z index of the biome in the matrix
     * @return Configured builder
     */
    public WorldProviderBuilder biome(BiomeGenBase biome, int x, int z) {
        provider.addBiome(biome, x, z);
        return this;
    }

    /**
     * Sets the Chunk gen supplier for the provider
     *
     * @param gen The chunk gen supplier to be used
     * @return Configured builder
     */
    public WorldProviderBuilder chunkGen(Supplier<IChunkProvider> gen) {
        provider.chunkGenSupplier = gen;
        return this;
    }

    /**
     * Sets the name of the world provider given a string
     *
     * @param name The required name
     * @return Configured builder
     */
    public WorldProviderBuilder name(String name) {
        provider.name = name;
        return this;
    }

    /**
     * Sets the name of the world provider given a planet ENUM
     *
     * @param planet The ENUM of the planet
     * @return Configured builder
     */
    public WorldProviderBuilder name(DimensionEnum planet) {
        provider.name = planet.getName();
        return this;
    }

    /**
     * Sets the sky color for the provider (RGB format)
     *
     * @param r The amount of red
     * @param g The amount of green
     * @param b The amount of blue
     * @return Configured builder
     */
    public WorldProviderBuilder skyColor(double r, double g, double b) {
        provider.skyColor = Vec3.createVectorHelper(r, g, b);
        return this;
    }

    /**
     * Sets the sunrise colour for the provider (RGBA format)
     *
     * @param r The amount of red
     * @param g The amount of green
     * @param b The amount of blue
     * @param a The amount of alpha
     * @return Configured builder
     */
    public WorldProviderBuilder sunriseSunsetColors(float r, float g, float b, float a) {
        provider.sunriseSunsetColors = new float[] { r, g, b, a };
        return this;
    }

    /**
     * Sets whether the sky should be coloured
     *
     * @param colored Boolean : True => Coloured sky
     * @return Configured builder
     */
    public WorldProviderBuilder skyColored(boolean colored) {
        provider.skyColored = colored;
        return this;
    }

    /**
     * Sets whether there should be void particles
     *
     * @param has Boolean : True => void particles
     * @return Configured builder
     */
    public WorldProviderBuilder worldHasVoidParticles(boolean has) {
        provider.worldHasVoidParticles = has;
        return this;
    }

    /**
     * Sets the y factor for the fog (how strong fog gets with height)
     *
     * @param factor The required y factor
     * @return Configured builder
     */
    public WorldProviderBuilder voidFogYFactor(double factor) {
        provider.voidFogYFactor = factor;
        return this;
    }

    /**
     * Sets whether to show fog in the X-Z plane
     *
     * @param show Boolean : True => Shows fog
     * @return Configured builder
     */
    public WorldProviderBuilder xzShowFog(boolean show) {
        provider.xzShowFog = show;
        return this;
    }

    /**
     * Sets the star brightness
     *
     * @param factor The factor by which to increase star brightness
     * @return Configured builder
     */
    public WorldProviderBuilder starBrightness(float factor) {
        provider.starBrightnessFactor = factor;
        return this;
    }

    /**
     * Sets the sun brightness
     *
     * @param factor The factor by which to increase sun brightness
     * @return Configured builder
     */
    public WorldProviderBuilder sunBrightness(float factor) {
        provider.sunBrightnessFactor = factor;
        return this;
    }

    /**
     * Sets the celestial cycle speed (sky-box background speed)
     *
     * @param speed The speed at which to cycle
     * @return Configured builder
     */
    public WorldProviderBuilder celestialCycleSpeed(float speed) {
        provider.celestialCycleSpeed = speed;
        return this;
    }

    /**
     * Sets the current moon phase
     *
     * @param phase The current moon phase required
     * @return Configured builder
     */
    public WorldProviderBuilder moonPhase(int phase) {
        if (phase < 0 || phase > 7) throw new IllegalArgumentException("Moon phase must be 0-7");
        provider.moonPhase = phase;
        return this;
    }

    /**
     * Sets whether compasses should spin wildly (like in the nether) or follow a point
     *
     * @param spin Boolean : True => Spinning compass
     * @return Configured builder
     */
    public WorldProviderBuilder mapSpin(boolean spin) {
        provider.mapSpin = spin;
        return this;
    }

    /**
     * Sets the respawn dimension for this planet
     *
     * @param dim The ID of the respawn dimension
     * @return Configured builder
     */
    public WorldProviderBuilder respawnDimension(int dim) {
        provider.respawnDimension = dim;
        return this;
    }

    /**
     * Sets whether you can respawn on this planet
     *
     * @param can Boolean : True => Can respawn here
     * @return Configured builder
     */
    public WorldProviderBuilder canRespawn(boolean can) {
        provider.canRespawn = can;
        return this;
    }

    public WorldProviderBuilder skyRenderer(IRenderHandler renderer) {
        provider.skyRenderer = renderer;
        return this;
    }

    /**
     * Sets the cloud renderer for the world
     *
     * @param renderer The renderer to be used
     * @return Configured builder
     */
    public WorldProviderBuilder cloudRenderer(IRenderHandler renderer) {
        provider.cloudRenderer = renderer;
        return this;
    }

    /**
     * Sets the weather renderer for the world
     *
     * @param renderer The renderer to be used
     * @return Configured builder
     */
    public WorldProviderBuilder weatherRenderer(IRenderHandler renderer) {
        provider.weatherRenderer = renderer;
        return this;
    }

    /**
     * Builds the provider by transferring biomes and applying all needed flags
     */
    public void build() {
        provider.applyFlags();
        provider.transferBiomes();
    }
}
