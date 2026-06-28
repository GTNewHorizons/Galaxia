package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.PlayableDimensionProfile;
import com.gtnewhorizons.galaxia.registry.dimension.SpaceStation;
import com.gtnewhorizons.galaxia.registry.dimension.asteroidbelts.FrozenBelt;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.planets.Mars;
import com.gtnewhorizons.galaxia.registry.dimension.planets.Moon;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;

import gregtech.api.enums.Materials;

public final class CelestialRegistry {

    private static final double EARTH_RADIUS_TO_AU = 23481;

    private static final Map<CelestialObjectId, CelestialObject> REGISTRATIONS = new LinkedHashMap<>();
    private static final Map<DimensionEnum, CelestialObjectId> IDS_BY_DIMENSION = new EnumMap<>(DimensionEnum.class);

    private static boolean bootstrapped;
    private static boolean frozen;

    public static CelestialHierarchy hierarchy;

    private CelestialRegistry() {}

    private static double seededPhase(@Nonnull String id) {
        long hash = id.hashCode() & 0xFFFFFFFFL;
        return (hash / (double) 0xFFFFFFFFL) * Math.PI * 2.0;
    }

    private static EffectBuilder dimensionEffects(int baseTemp, int oxygenPercent, int pressure) {
        return EffectBuilder.builder()
            .baseTemp(baseTemp)
            .oxygenPercent(oxygenPercent)
            .pressure(pressure)
            .build();
    }

    private static Fluid requiredGas(Materials material) {
        FluidStack stack = material.getGas(1L);
        Fluid fluid = stack != null ? stack.getFluid() : null;
        if (fluid == null) throw new IllegalStateException("Required atmosphere gas is not available: " + material);
        return fluid;
    }

    private static AsteroidFieldProfile frozenBeltAsteroidField() {
        return AsteroidFieldProfile.builder()
            .seedSalt(0xF20A3E11L)
            .generationVersion(1)
            .sizeCounts(1, 2, 3)
            .radialBand(2.15 * EARTH_RADIUS_TO_AU, 2.45 * EARTH_RADIUS_TO_AU)
            .oreProfile(new AsteroidOreProfile("metallic", 3.0, List.of("ore.mix.iron")))
            .oreProfile(new AsteroidOreProfile("volatile_ice", 2.0, List.of("ore.mix.lapis")))
            .oreProfile(new AsteroidOreProfile("rare_crystal", 1.0, List.of("ore.mix.redstone")))
            .build();
    }

    private static PlayableDimensionProfile.Builder stationBuildable(PlayableDimensionProfile.Builder builder) {
        return builder.addValidSpaceStationBlocks(
            GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get(),
            GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
            GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
            GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());
    }

    public static void registerDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;
        PlanetaryFeatureRegistry.registerDefaults();

        register(
            CelestialObjectId.NOVA_CAELUM,
            builder -> builder.objectClass(CelestialObject.Class.GALAXY)
                .properties(
                    b -> b.orbitalGravity(5.4e8, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("mapLayer", "stars")));

        register(
            CelestialObjectId.VAEL,
            builder -> builder.parent(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(0.0, 0.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(1.0)
                .properties(
                    b -> b.orbitalGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "vael")));

        register(
            CelestialObjectId.ILIA,
            builder -> builder.parent(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(5800.0, -2600.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.92)
                .properties(
                    b -> b.orbitalGravity(4.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "ilia")));

        register(
            CelestialObjectId.PROXIMA_CENTAURI,
            builder -> builder.parent(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(-4900.0, 3400.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.88)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "proxima_centauri")));

        register(
            CelestialObjectId.ROMULUS,
            builder -> builder.parent(CelestialObjectId.ILIA)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.296 * EARTH_RADIUS_TO_AU, 0.00031, seededPhase("ilia_romulus"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.24)
                .properties(
                    b -> b.orbitalGravity(5.2e6, 1200.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(301)
                        .radiation(0.08)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("status", "placeholder_colony_world"))
                .featureTileChance(0.18)
                .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 3.0)
                .feature(PlanetaryFeatureRegistry.STABLE_BEDROCK, 2.0)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.5)
                .feature(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET, 0.6));

        register(
            CelestialObjectId.REMUS,
            builder -> builder.parent(CelestialObjectId.ILIA)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.726 * EARTH_RADIUS_TO_AU, 0.00018, seededPhase("ilia_remus"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.19)
                .properties(
                    b -> b.orbitalGravity(4.6e6, 1500.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(182)
                        .radiation(0.14)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined"))
                .featureTileChance(0.24)
                .feature(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET, 4.0)
                .feature(PlanetaryFeatureRegistry.THERMAL_SINK_ZONE, 2.0)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.0));

        register(
            CelestialObjectId.EGORA,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.92 * EARTH_RADIUS_TO_AU, 0.00022, seededPhase("egora"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.orbitalGravity(9.8e6, 2400.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(288)
                        .radiation(0.05)
                        .oreProfile("undefined")
                        .gtOreVeinIds("ore.mix.lapis", "ore.mix.iron", "ore.mix.redstone")
                        .metadata("surface", "undefined")
                        .metadata("status", "placeholder_homeworld"))
                .featureTileChance(0.20)
                .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 2.0)
                .feature(PlanetaryFeatureRegistry.STABLE_BEDROCK, 1.5)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 2.0)
                .feature(PlanetaryFeatureRegistry.VOLATILE_DEPOSIT, 0.2));

        register(
            DimensionEnum.MARS,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(1.52 * EARTH_RADIUS_TO_AU, 0.00011, seededPhase("mars"))
                .texture(EnumTextures.ICON_MARS.get())
                .spriteSize(0.825)
                .properties(
                    b -> b.orbitalGravity(5.5e8, 9500.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .localGravityG(0.25)
                        .massEarthRelative(0.25)
                        .orbitalRadiusEarthRelative(1.52 * EARTH_RADIUS_TO_AU)
                        .radiusEarthRelative(0.53)
                        .temperature(67)
                        .radiation(0.10)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined"))
                .playableDimensionProfile(
                    stationBuildable(
                        PlayableDimensionProfile.builder(DimensionEnum.MARS)
                            .airResistance(0.1)
                            .effects(dimensionEffects(67, 0, 0))
                            .tier(EnumTiers.TIER_2)
                            .worldGeneration(Mars::configureWorldProvider)).build())
                .featureTileChance(0.16)
                .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 4.0)
                .feature(PlanetaryFeatureRegistry.STABLE_BEDROCK, 2.0)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 1.0));

        register(
            DimensionEnum.MOON,
            builder -> builder.parent(CelestialObjectId.MARS)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(0.27 * EARTH_RADIUS_TO_AU, 0.00145, seededPhase("moon"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.06)
                .properties(
                    b -> b.orbitalGravity(1.8e6, 480.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .localGravityG(0.25)
                        .massEarthRelative(0.012)
                        .orbitalRadiusEarthRelative(EARTH_RADIUS_TO_AU)
                        .radiusEarthRelative(0.27)
                        .temperature(225)
                        .radiation(0.18)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined"))
                .playableDimensionProfile(
                    stationBuildable(
                        PlayableDimensionProfile.builder(DimensionEnum.MOON)
                            .airResistance(0.01)
                            .celestialBodies(
                                Moon.buildSky()
                                    .build())
                            .effects(dimensionEffects(225, 0, 0))
                            .tier(EnumTiers.TIER_1)
                            .worldGeneration(Moon::configureWorldProvider)).build())
                .featureTileChance(0.14)
                .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 5.0)
                .feature(PlanetaryFeatureRegistry.STABLE_BEDROCK, 2.0)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 0.8));

        register(
            CelestialObjectId.FROZEN_BELT,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObject.Class.ASTEROID_BELT)
                .circularOrbit(2.30 * EARTH_RADIUS_TO_AU, 0.00005, seededPhase("frozen_belt"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.60)
                .properties(
                    b -> b.orbitalGravity(3.5e5, 3000.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .localGravityG(0.0)
                        .temperature(67)
                        .radiation(0.28)
                        .oreProfile("undefined")
                        .asteroidFieldProfile(frozenBeltAsteroidField())
                        .metadata("surface", "undefined")
                        .metadata("minorBodies", "enabled"))
                .playableDimensionProfile(
                    stationBuildable(
                        PlayableDimensionProfile.builder(DimensionEnum.FROZEN_BELT)
                            .provider(WorldProviderSpace.class)
                            .airResistance(0.0)
                            .effects(dimensionEffects(67, 0, 0))
                            .worldGeneration(FrozenBelt::configureWorldProvider)).build())
                .featureTileChance(0.34)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 4.0)
                .feature(PlanetaryFeatureRegistry.RARE_CRYSTAL_FORMATION, 1.2)
                .feature(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET, 1.0)
                .feature(PlanetaryFeatureRegistry.VOLATILE_DEPOSIT, 0.3));

        register(
            CelestialObjectId.AMBERGRIS_FRAGMENT,
            builder -> builder.parent(CelestialObjectId.FROZEN_BELT)
                .objectClass(CelestialObject.Class.ASTEROID)
                .circularOrbit(0.18 * EARTH_RADIUS_TO_AU, 0.00091, seededPhase("ambergris_fragment"))
                .texture(EnumTextures.ICON_AMBERGRIS.get())
                .spriteSize(0.05)
                .properties(
                    b -> b.orbitalGravity(6.0e4, 140.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(true)
                        .temperature(41)
                        .radiation(0.52)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("sizeClass", "minor")));

        register(
            CelestialObjectId.OVERWORLD,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.45 * EARTH_RADIUS_TO_AU, 0.00022, seededPhase("vitris"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.orbitalGravity(1, 2400.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .localGravityG(1.0)
                        .orbitalRadiusEarthRelative(EARTH_RADIUS_TO_AU)
                        .surfacePressurePa(1.0)
                        .addAtmosphereIngredient(requiredGas(Materials.Air), 1.0)
                        .temperature(288)
                        .radiation(0.00)
                        .oreProfile("undefined")
                        .gtOreVeinIds("ore.mix.lapis", "ore.mix.iron", "ore.mix.redstone")
                        .metadata("surface", "undefined")
                        .metadata("status", "placeholder_homeworld"))
                .playableDimensionProfile(
                    stationBuildable(
                        PlayableDimensionProfile.builder(DimensionEnum.OVERWORLD)
                            .effects(
                                EffectBuilder.builder()
                                    .build())
                            .tier(EnumTiers.TIER_1)
                            .worldGeneration(CelestialRegistry::configureOverworldProvider)).build())
                .featureTileChance(0.20)
                .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 2.0)
                .feature(PlanetaryFeatureRegistry.STABLE_BEDROCK, 1.5)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 2.0)
                .feature(PlanetaryFeatureRegistry.MAGMA_POOL, 0.4)
                .feature(PlanetaryFeatureRegistry.VOLATILE_DEPOSIT, 0.2));

        register(
            DimensionEnum.OVERWORLD_ORBIT,
            builder -> builder.parent(CelestialObjectId.OVERWORLD)
                .objectClass(CelestialObject.Class.STATION)
                .circularOrbit(0.04 * EARTH_RADIUS_TO_AU, 0.00260, seededPhase("overworld_orbit"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.08)
                .properties(
                    b -> b.orbitalGravity(0.0, 90.0)
                        .visitable(true)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .localGravityG(0.0)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("stationRole", "orbital_logistics"))
                .playableDimensionProfile(
                    PlayableDimensionProfile.builder(DimensionEnum.OVERWORLD_ORBIT)
                        .provider(WorldProviderSpace.class)
                        .airResistance(0.0)
                        .effects(dimensionEffects(67, 0, 0))
                        .tier(EnumTiers.TIER_1)
                        .worldGeneration(SpaceStation::configureWorldProvider)
                        .build()));
    }

    private static void configureOverworldProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .name(DimensionEnum.OVERWORLD);
    }

    public static void register(CelestialObjectId id, @Nonnull Consumer<CelestialObject.Builder> registrationBuilder) {
        CelestialObject.Builder builder = CelestialObject.builder()
            .id(id);

        registrationBuilder.accept(builder);
        register(builder.build());
    }

    public static void register(DimensionEnum dimension,
        @Nonnull Consumer<CelestialObject.Builder> registrationBuilder) {
        CelestialObject.Builder builder = CelestialObject.builder()
            .dimension(dimension);
        registrationBuilder.accept(builder);
        register(builder.build());
    }

    public static void register(@Nonnull CelestialObject registration) {
        assertMutable();
        validateRegistration(registration, null);
        REGISTRATIONS.put(registration.id(), registration);
        if (registration.dimensionEnum() != null) {
            IDS_BY_DIMENSION.put(registration.dimensionEnum(), registration.id());
        }
    }

    public static void freezeAndBake() {
        registerDefaults();
        if (frozen) return;

        hierarchy = CelestialHierarchy.builder()
            .add(getAll())
            .build();

        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static Optional<CelestialObject> get(CelestialObjectId id) {
        return Optional.ofNullable(REGISTRATIONS.get(id));
    }

    public static List<CelestialObject> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRATIONS.values()));
    }

    public static List<CelestialObject> getPlayableBodies() {
        registerDefaults();
        return REGISTRATIONS.values()
            .stream()
            .filter(body -> body.playableDimensionProfile() != null)
            .toList();
    }

    public static List<CelestialObject> getRoots() {
        return hierarchy.roots();
    }

    public static CelestialObject getPrimaryRoot() {
        List<CelestialObject> roots = getRoots();
        if (roots.isEmpty()) throw new IllegalStateException("No celestial objects have been registered");
        return roots.get(0);
    }

    public static Optional<CelestialObject> findByDimension(DimensionEnum dimension) {
        registerDefaults();
        CelestialObjectId objectId = IDS_BY_DIMENSION.get(dimension);
        if (objectId == null) return Optional.empty();
        return Optional.ofNullable(
            hierarchy.bodiesById()
                .get(objectId));
    }

    public static Optional<CelestialObject> findById(CelestialObjectId id) {
        registerDefaults();
        return Optional.ofNullable(
            hierarchy.bodiesById()
                .get(id));
    }

    private static void validateRegistration(CelestialObject registration, CelestialObjectId existingId) {
        if (REGISTRATIONS.containsKey(registration.id()) && !registration.id()
            .equals(existingId)) {
            throw new IllegalArgumentException("Duplicate celestial object id: " + registration.id());
        }
        if (registration.parentId() != null && registration.parentId()
            .equals(registration.id())) {
            throw new IllegalArgumentException("Celestial object cannot orbit itself: " + registration.id());
        }
        if (registration.parentId() != null && !REGISTRATIONS.containsKey(registration.parentId())) {
            throw new IllegalArgumentException("Unknown parent celestial object id: " + registration.parentId());
        }
        if (registration.dimensionEnum() != null) {
            CelestialObjectId existingDimensionOwner = IDS_BY_DIMENSION.get(registration.dimensionEnum());
            if (existingDimensionOwner != null && !existingDimensionOwner.equals(existingId)) {
                throw new IllegalArgumentException("Duplicate dimension mapping for " + registration.dimensionEnum());
            }
        }
    }

    private static void assertMutable() {
        if (frozen) throw new IllegalStateException("Celestial registry is frozen and can no longer be modified");
    }
}
