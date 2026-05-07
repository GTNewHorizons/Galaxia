package com.gtnewhorizons.galaxia.registry.dimension.cave;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.TubeNoise;

import java.util.Random;

public class CaveShapeTubes implements CaveShape {

    private static TubeNoise caveNoise;

    @Override
    public void prepareCaveShape(Random random) {
        caveNoise = new TubeNoise();
        caveNoise.setSeed(random);
    }

    @Override
    public boolean preparedCaveShape() {
        return caveNoise != null;
    }

    @Override
    public void prepareCaveCache(int chunkX, int chunkZ) {
        caveNoise.updateCache(chunkX, chunkZ);
    }

    @Override
    public boolean preparedCaveCache(int chunkX, int chunkZ) {
        return caveNoise.isCached() && !caveNoise.isInDifferentChunk(chunkX, chunkZ);
    }

    @Override
    public boolean generateCave(int localX, int localY, int localZ, int height) {
        return caveNoise.isIntersectingTube(localX, localZ);
    }
}
