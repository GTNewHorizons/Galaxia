package com.gtnewhorizons.galaxia.registry.dimension;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public final class CelestialDimensionMaterializer {

    private CelestialDimensionMaterializer() {}

    public static DimensionDef materializeDefinition(@Nonnull CelestialObject body) {
        PlayableDimensionProfile profile = body.playableDimensionProfile();
        if (profile == null) {
            throw new IllegalStateException("Cannot materialize non-playable celestial body: " + body.id());
        }

        DimensionEnum dimension = profile.dimension();
        return new DimensionDef(
            dimension.getName(),
            dimension.getId(),
            profile.provider(),
            profile.keepLoaded(),
            profile.gravity(),
            profile.airResistance(),
            profile.removeSpeedCancelation(),
            profile.celestialBodies(),
            profile.effects(),
            profile.mass(),
            profile.orbitalRadius(),
            profile.radius(),
            profile.tier(),
            profile.skyboxTexture(),
            profile.validSpaceStationBlocks());
    }
}
