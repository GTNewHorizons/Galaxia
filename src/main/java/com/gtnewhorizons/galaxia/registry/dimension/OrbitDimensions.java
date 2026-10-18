package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.core.config.ConfigDimensions;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.dimension.asteroidbelts.ChunkProviderAsteroidBelt;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.dimension.sky.CelestialBody;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenAsteroid;

/**
 * Orbit dimensions generated above every playable body, unless its profile calls {@code forbidOrbit()}. An orbit is
 * empty space with no gravity and no air, the parent body fills the sky below, and it is the only place space
 * stations can be built.
 * <p>
 * An orbit belongs to its parent body: looking up the celestial object of an orbit dimension gives the planet, so
 * assets, satellites and logistics treat the orbit as part of that planet. Dimension ids follow the order of
 * {@link DimensionEnum}, starting at {@link ConfigDimensions#orbitBaseId}
 */
public final class OrbitDimensions {

    private static final Map<Integer, Orbit> BY_DIMENSION = new LinkedHashMap<>();
    private static final Map<CelestialObjectKey, Orbit> BY_BODY = new HashMap<>();
    private static final Map<Integer, DimensionDef> DEFINITIONS = new HashMap<>();
    private static final WorldGenAsteroid[] NO_ASTEROIDS = new WorldGenAsteroid[0];
    private static BiomeGenSpace biome;

    private OrbitDimensions() {}

    /**
     * Orbit around one body
     *
     * @param dimensionId Dimension id of the orbit
     * @param body        The body below
     * @param surface     Dimension of the body's surface
     */
    public record Orbit(int dimensionId, CelestialObject body, DimensionEnum surface) {

        /** Internal name, also used by the world provider */
        public String name() {
            return surface.getName() + "_Orbit";
        }

        /** Name shown to players, e.g. "Mars Orbit" */
        public String displayName() {
            return StatCollector.translateToLocalFormatted(
                "galaxia.dimension.orbit",
                StatCollector.translateToLocal(surface.getTranslationKey()));
        }
    }

    /**
     * Creates the orbit of a body, called while the playable dimensions are registered
     *
     * @param body A playable body
     *
     * @return The orbit, empty when the body forbids one
     */
    static Optional<Orbit> create(CelestialObject body) {
        PlayableDimensionProfile profile = body.playableDimensionProfile();
        if (profile == null || !profile.hasOrbit()) return Optional.empty();
        Orbit orbit = new Orbit(
            ConfigDimensions.orbitBaseId + profile.dimension()
                .ordinal(),
            body,
            profile.dimension());
        BY_DIMENSION.put(orbit.dimensionId(), orbit);
        BY_BODY.put(body.key(), orbit);
        return Optional.of(orbit);
    }

    public static boolean isOrbit(int dimensionId) {
        return BY_DIMENSION.containsKey(dimensionId);
    }

    public static Optional<Orbit> byDimension(int dimensionId) {
        return Optional.ofNullable(BY_DIMENSION.get(dimensionId));
    }

    /** Orbit above a body, empty if the body has none */
    public static Optional<Orbit> ofBody(CelestialObjectKey body) {
        return Optional.ofNullable(BY_BODY.get(body));
    }

    public static Collection<Orbit> all() {
        return Collections.unmodifiableCollection(BY_DIMENSION.values());
    }

    /** Blocks the walls of station rooms may be made of */
    public static List<Block> stationBlocks() {
        return List.of(
            GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get(),
            GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
            GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
            GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());
    }

    /**
     * Dimension definition of an orbit: weightless vacuum at the temperature and radiation of the body, with the body
     * filling the sky below
     *
     * @param dimensionId Dimension id of the orbit
     *
     * @return The definition, empty for other dimensions
     */
    public static Optional<DimensionDef> definition(int dimensionId) {
        Orbit orbit = BY_DIMENSION.get(dimensionId);
        if (orbit == null) return Optional.empty();
        return Optional.of(DEFINITIONS.computeIfAbsent(dimensionId, id -> buildDefinition(orbit)));
    }

    private static DimensionDef buildDefinition(Orbit orbit) {
        DimensionDef surface = CelestialDimensionMaterializer.materializeDefinition(orbit.body());
        EffectBuilder surfaceEffects = surface.effects();
        EffectBuilder vacuum = EffectBuilder.builder()
            .baseTemp(surfaceEffects.getBaseTemp())
            .radiation(surfaceEffects.getRadiation())
            .oxygenPercent(0)
            .pressure(0)
            .build();
        return new DimensionDef(
            orbit.name(),
            orbit.dimensionId(),
            WorldProviderSpace.class,
            false,
            0.0,
            0.0,
            true,
            sky(orbit),
            vacuum,
            surface.mass(),
            surface.orbitalRadius(),
            surface.radius(),
            surface.tier(),
            surface.skyboxTexture(),
            stationBlocks());
    }

    /** Distance of the body below, close enough that it stays inside the far plane at low render distances */
    private static final double BODY_BELOW_DISTANCE = 30.0;
    /** Half the size of a body filling its whole picture, wide enough to reach 60 degrees out from straight down */
    private static final float BODY_BELOW_SIZE = (float) (BODY_BELOW_DISTANCE * Math.tan(Math.toRadians(60)));
    /** Sky pictures draw the body over the middle quarter, the rest is its glow */
    private static final float SKY_PICTURE_SCALE = 4f;

    /**
     * The sun above and the parent body straight below, filling most of the view down. Bodies drawn at a negative
     * distance sit under the horizon, and with no orbital period of note the body stays put
     */
    private static List<CelestialBody> sky(Orbit orbit) {
        return SkyBuilder.builder()
            .addBody(
                s -> s.texture("minecraft:textures/environment/sun.png")
                    .size(30f)
                    .distance(100.0)
                    .inclination(45)
                    .period(24000L)
                    .mainLightSource())
            .addBody(
                p -> p.texture(view(orbit))
                    .size(hasOrbitView(orbit) ? BODY_BELOW_SIZE * SKY_PICTURE_SCALE : BODY_BELOW_SIZE)
                    .distance(-BODY_BELOW_DISTANCE)
                    .period(Long.MAX_VALUE)
                    .horizon(hasOrbitView(orbit) ? 1f / SKY_PICTURE_SCALE : 1f))
            .build();
    }

    /** Sky pictures leave room for a glow around the body, starmap icons fill the whole square */
    private static boolean hasOrbitView(Orbit orbit) {
        PlayableDimensionProfile profile = orbit.body()
            .playableDimensionProfile();
        return profile != null && profile.orbitView() != null;
    }

    private static ResourceLocation view(Orbit orbit) {
        PlayableDimensionProfile profile = orbit.body()
            .playableDimensionProfile();
        return profile != null && profile.orbitView() != null ? profile.orbitView()
            : orbit.body()
                .texture();
    }

    /**
     * Horizon of an orbit, far enough down that the dark sky plane always lies past the far plane and never covers the
     * planet below
     */
    private static final double ORBIT_HORIZON = -1_000_000;

    /**
     * World provider of an orbit: an empty void with a dark sky and no sunrise glow
     */
    static void configureWorldProvider(Orbit orbit, WorldProviderBuilder builder) {
        builder.sky(true)
            .skyColor(0, 0, 0)
            .fog(0, 0, 0)
            .sunriseSunsetColors(0, 0, 0, 0)
            .biome(biome(), 0, 0)
            .name(orbit.name())
            .cloudHeight(Integer.MIN_VALUE)
            .horizon(ORBIT_HORIZON)
            .chunkGen(
                () -> new ChunkProviderAsteroidBelt(
                    builder.provider().worldObj,
                    builder.provider().worldObj.getSeed(),
                    NO_ASTEROIDS))
            .build();
    }

    /** All orbits share one biome, so reopening worlds does not use up biome ids */
    private static BiomeGenSpace biome() {
        if (biome == null) {
            int id = BiomeIdOffsetter.getBiomeId();
            biome = new BiomeGenSpace(
                id,
                new BiomeGenBuilder(id).name("Orbit")
                    .temperature(0.0F)
                    .rainfall(0));
        }
        return biome;
    }
}
