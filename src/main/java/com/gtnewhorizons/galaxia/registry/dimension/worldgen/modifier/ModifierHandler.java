package com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier;

import java.util.Arrays;

import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.Smoothstep;

/**
 * Handles all terrain modifiers and intervals on them
 */
public class ModifierHandler {

    private final NoiseGeneratorOctaves weirdnessNoise;

    private double[] weirdnessCache = defaultModifier();

    /**
     * Creates a modifier handler
     * 
     * @param world World with the required seed
     */
    public ModifierHandler(World world) {
        weirdnessNoise = new NoiseGeneratorOctaves(new StdLCG(world.getSeed()), 4);
    }

    /**
     * Fills an array with default values of 1 (neutral multiplier)
     * 
     * @return Array full of default values
     */
    private static double[] defaultModifier() {
        double[] defaultModifier = new double[256];
        Arrays.fill(defaultModifier, 1);
        return defaultModifier;
    }

    /**
     * Generates modifier values of a whole chunk for a given modifier entry
     * 
     * @param modifierEntry Modifier entry with specific requirements
     * @param chunkX        x coordinate of the chunk
     * @param chunkZ        z coordinate of the chunk
     * @return Array full of modifier values
     */
    public double[] assignModifierValues(TerrainModifierEntry modifierEntry, int chunkX, int chunkZ) {
        double[] valueArray = defaultModifier();
        if (modifierEntry == null) {
            return valueArray;
        }
        if (modifierEntry.modifier() == TerrainModifier.WEIRDNESS) {
            cacheWeirdness(chunkX, chunkZ);
            cacheWeirdnessMultiplier(modifierEntry.lowerRange(), modifierEntry.upperRange());
        }
        return weirdnessCache;
    }

    /**
     * Generates a cache of raw weirdness values
     * 
     * @param chunkX x coordinate of the chunk
     * @param chunkZ z coordinate of the chunk
     */
    private void cacheWeirdness(int chunkX, int chunkZ) {
        chunkX *= 16;
        chunkZ *= 16;
        weirdnessCache = weirdnessNoise.generateNoiseOctaves(new double[256], chunkZ, chunkX, 16, 16, 0.02, 0.02, 0);
        for (int i = 0; i < weirdnessCache.length; i++) {
            double localNoise = weirdnessCache[i];
            localNoise /= 4;
            if (localNoise < -2) {
                localNoise = -2;
            } else if (localNoise > 2) {
                localNoise = 2;
            }
            weirdnessCache[i] = localNoise;
        }
    }

    /**
     * Converts raw values into a range from 0 to 1 with a peak in the middle within an interval
     * 
     * @param minimum Lower limit of the interval
     * @param maximum Upper limit of the interval
     */
    private void cacheWeirdnessMultiplier(double minimum, double maximum) {
        double intervalLength = maximum - minimum;
        double halfLength = intervalLength / 2;
        double peakValue = minimum + halfLength;
        // Determines how quickly the value should fade if it is not at the center
        double diminishingFactor = 1 / halfLength;
        for (int i = 0; i < weirdnessCache.length; i++) {
            if (halfLength == 0) {
                weirdnessCache[i] = 0;
            } else {
                // Calculate distance from the central peak and smoothen the result
                weirdnessCache[i] = Smoothstep
                    .apply(Math.max(0, 1 - diminishingFactor * Math.abs(peakValue - weirdnessCache[i])));
            }
        }
    }
}
