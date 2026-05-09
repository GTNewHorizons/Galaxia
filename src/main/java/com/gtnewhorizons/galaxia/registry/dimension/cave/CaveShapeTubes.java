package com.gtnewhorizons.galaxia.registry.dimension.cave;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.TubeNoise;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import java.util.Random;

public class CaveShapeTubes implements CaveShape {
    private static final int CHUNK_AREA = 256;
    private static final int CHUNK_WIDTH = 16;
    private static final double HORIZONTAL_CAVE_STRETCH = 0.1;

    private static TubeNoise caveNoise;
    private static NoiseGeneratorOctaves sizeNoise;

    private final double[] sizeModifiers = new double[CHUNK_AREA];

    @Override
    public void prepareCaveShape(Random random) {
        sizeNoise = new NoiseGeneratorOctaves(random, 4);
        caveNoise = new TubeNoise();
        caveNoise.setSeed(random);
    }

    @Override
    public boolean preparedCaveShape() {
        return caveNoise != null && sizeNoise != null;
    }

    @Override
    public void prepareCaveCache(int chunkX, int chunkZ) {
        caveNoise.updateCache(chunkX, chunkZ);
        double[] rawModifiers = sizeNoise.generateNoiseOctaves(
            new double[CHUNK_AREA],
            chunkZ * CHUNK_WIDTH,
            chunkX * CHUNK_WIDTH,
            CHUNK_WIDTH,
            CHUNK_WIDTH,
            HORIZONTAL_CAVE_STRETCH,
            HORIZONTAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < rawModifiers.length; i++) {
            double noise = rawModifiers[i];
            noise += 8;
            noise /= 8;
            noise += 0.25;
            sizeModifiers[i] = noise;
        }
    }

    @Override
    public boolean preparedCaveCache(int chunkX, int chunkZ) {
        return caveNoise.isCached() && !caveNoise.isInDifferentChunk(chunkX, chunkZ);
    }

    @Override
    public boolean generateCave(int localX, int localY, int localZ, int height) {
        return caveNoise.isIntersectingTube(localX, localY, localZ, sizeModifiers[localX + (localZ << 4)]);
    }
}
