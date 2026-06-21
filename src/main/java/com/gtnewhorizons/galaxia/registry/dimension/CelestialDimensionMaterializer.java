package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;

import cpw.mods.fml.common.FMLLog;

public final class CelestialDimensionMaterializer {

    private static boolean registered;

    private CelestialDimensionMaterializer() {}

    public static Optional<DimensionDef> findDefinitionById(int dimensionId) {
        DimensionEnum dimension = DimensionEnum.fromId(dimensionId);
        if (dimension == null) return Optional.empty();
        return CelestialRegistry.findByDimension(dimension)
            .filter(body -> body.playableDimensionProfile() != null)
            .map(CelestialDimensionMaterializer::materializeDefinition);
    }

    public static void registerPlayableDimensions() {
        if (registered) return;
        registered = true;

        int count = 0;
        for (CelestialObject body : CelestialRegistry.getPlayableBodies()) {
            PlayableDimensionProfile profile = body.playableDimensionProfile();
            DimensionDef def = materializeDefinition(body);
            registerDimension(def, profile);
            count++;
        }

        FMLLog.info("[Galaxia] Registered %d celestial dimensions", count);
    }

    private static void registerDimension(DimensionDef def, PlayableDimensionProfile profile) {
        int id = def.id();

        WorldProviderSpace.registerConfigurator(id, profile.worldGenerationAdapter()::configure);
        DimensionManager.registerProviderType(id, def.provider(), def.keepLoaded());

        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
            FMLLog.info("[Galaxia] Registered dimension %s (ID %d)", def.name(), id);
        } else {
            FMLLog.warning("[Galaxia] Dimension ID %d already taken!", id);
        }
    }

    public static DimensionDef materializeDefinition(@Nonnull CelestialObject body) {
        PlayableDimensionProfile profile = body.playableDimensionProfile();
        if (profile == null) {
            throw new IllegalStateException("Cannot materialize non-playable celestial body: " + body.id());
        }

        DimensionEnum dimension = profile.dimension();
        CelestialBodyProperties properties = body.properties();
        return new DimensionDef(
            dimension.getName(),
            dimension.getId(),
            profile.provider(),
            profile.keepLoaded(),
            properties.localGravityG(),
            profile.airResistance(),
            profile.removeSpeedCancelation(),
            profile.celestialBodies(),
            profile.effects(),
            properties.massEarthRelative(),
            properties.orbitalRadiusEarthRelative(),
            properties.radiusEarthRelative(),
            profile.tier(),
            profile.skyboxTexture(),
            profile.validSpaceStationBlocks());
    }
}
