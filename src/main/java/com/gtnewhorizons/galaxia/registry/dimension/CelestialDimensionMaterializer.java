package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.List;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

public final class CelestialDimensionMaterializer {

    private CelestialDimensionMaterializer() {}

    public static List<DimensionDef> materializePlayableDefinitions() {
        return CelestialRegistry.getPlayableBodies()
            .stream()
            .map(CelestialDimensionMaterializer::materializeDefinition)
            .toList();
    }

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
