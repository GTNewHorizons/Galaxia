package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.progress.ProgressJobRunner;

public record AsteroidFieldScanWork(@Nonnull AsteroidFieldScanPass pass, @Nonnull MinorCelestialBodyId asteroidId)
    implements ProgressJobRunner.Work {

    static AsteroidFieldScanWork from(@Nonnull AsteroidFieldScanPass pass, @Nonnull AsteroidFieldNode node) {
        return new AsteroidFieldScanWork(pass, node.id());
    }

    @Override
    public int durationTicks() {
        return pass.durationTicks();
    }
}
