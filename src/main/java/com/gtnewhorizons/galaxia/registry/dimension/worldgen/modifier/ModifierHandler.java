package com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier;

import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainModifier;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainModifierEntry;
import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import java.util.Arrays;

public class ModifierHandler {
    private final NoiseGeneratorOctaves weirdnessNoise;

    private double[] weirdnessCache = defaultModifier();

    public ModifierHandler(World world) {
        weirdnessNoise = new NoiseGeneratorOctaves(new StdLCG(world.getSeed()), 4);
    }

    private static double[] defaultModifier() {
        double[] defaultModifier = new double[256];
        Arrays.fill(defaultModifier, 1);
        return defaultModifier;
    }

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
            if (localNoise > 1.75) {
                System.out.println("Upper extreme");
            } else if (localNoise < -1.75) {
                System.out.println("Lower extreme");
            }
            weirdnessCache[i] = localNoise;
        }
    }

    private void cacheWeirdnessMultiplier(double minimum, double maximum) {
        double intervalLength = maximum - minimum;
        double halfLength = intervalLength / 2;
        double peakValue = minimum + halfLength;
        double diminishingFactor = 1 / halfLength;
        for (int i = 0; i < weirdnessCache.length; i++) {
            if (halfLength == 0) {
                weirdnessCache[i] = 0;
            } else {
                weirdnessCache[i] = Math.max(0, 1 - diminishingFactor * Math.abs(peakValue - weirdnessCache[i]));
            }
        }
    }
}
