package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

public interface NoiseSampler {

    double sample(double x, double y);

    double sample(double x, double y, double z);

}
