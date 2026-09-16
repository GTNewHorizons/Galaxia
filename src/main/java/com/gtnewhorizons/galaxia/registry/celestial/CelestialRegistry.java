package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidCelestialMaterializer;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.content.GeneratedAsteroids;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.content.LoreAsteroids;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.PlayableDimensionProfile;
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
    // JPL mean satellite elements: https://ssd.jpl.nasa.gov/sats/elem/
    private static final double EARTH_RADIUS_KM = 6371.0;
    // JPL gravitational parameters use km^3/s^2. Keep Sol's existing simulation time scale.
    private static final double GRAVITY_SCALE = 7.2e7 / 132712440041.279419;

    private static final Map<CelestialObjectKey, CelestialObject> REGISTRATIONS = new LinkedHashMap<>();
    private static final Map<DimensionEnum, CelestialObjectKey> IDS_BY_DIMENSION = new EnumMap<>(DimensionEnum.class);

    private static boolean bootstrapped;
    private static boolean frozen;

    private static CelestialHierarchy hierarchy;

    private CelestialRegistry() {}

    /**
     * Baked parent/child view of the registry. Only available after {@link #freezeAndBake()}, which runs during mod
     * init; asking earlier is a lifecycle bug, so it fails loudly instead of throwing NPE at the call site.
     */
    public static CelestialHierarchy hierarchy() {
        if (hierarchy == null) {
            throw new IllegalStateException("Celestial hierarchy is not baked yet; freezeAndBake() has not run");
        }
        return hierarchy;
    }

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
            CelestialObjectId.NOVUM_CAELUM,
            builder -> builder.objectClass(CelestialObject.Class.GALAXY)
                .properties(
                    b -> b.orbitalGravity(5.4e8, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("mapLayer", "stars")));

        register(
            CelestialObjectId.SOL,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(0.0, 0.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(1.0)
                .properties(
                    b -> b.orbitalGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "sol")));

        register(
            CelestialObjectId.BARNARDA,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(5800.0, -2600.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.92)
                .properties(
                    b -> b.orbitalGravity(4.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "barnarda")));

        register(
            CelestialObjectId.TCETI_A,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(-4900.0, 3400.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.88)
                .properties(
                    b -> b.orbitalGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "tceti")));

        register(
            CelestialObjectId.VEGA,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(7600.0, 3300.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(1.08)
                .properties(
                    b -> b.orbitalGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "vega")));

        register(
            CelestialObjectId.ALPHA_CENTAURI_A,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(-7200.0, -3100.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.96)
                .properties(
                    b -> b.orbitalGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "alpha_centauri")));

        register(
            CelestialObjectId.VAEL,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(15000.0, -5000.0)
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
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(15000.0, 5000.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.92)
                .properties(
                    b -> b.orbitalGravity(4.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "ilia")));

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
                        .gtOreDepositIds("ore.mix.lapis", "ore.mix.iron", "ore.mix.redstone")
                        .metadata("surface", "undefined")
                        .metadata("status", "placeholder_homeworld"))
                .featureTileChance(0.20)
                .feature(PlanetaryFeatureRegistry.REGOLITH_FLATS, 2.0)
                .feature(PlanetaryFeatureRegistry.STABLE_BEDROCK, 1.5)
                .feature(PlanetaryFeatureRegistry.MINERAL_VEIN, 2.0)
                .feature(PlanetaryFeatureRegistry.VOLATILE_DEPOSIT, 0.2));

        register(
            CelestialObjectId.TENEBRAE,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(1.20 * EARTH_RADIUS_TO_AU, 0.00016, seededPhase("tenebrae"))
                .texture(EnumTextures.ICON_TENEBRAE.get())
                .spriteSize(0.20));

        register(
            CelestialObjectId.MERCURY,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.387 * EARTH_RADIUS_TO_AU, 0.00028, seededPhase("mercury"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.12)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(440)
                        .radiation(0.30)
                        .oreProfile("gtnh_t4")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.iron",
                            "ore.mix.bauxite",
                            "ore.mix.redstone",
                            "ore.mix.molybdenum",
                            "ore.mix.diamond",
                            "ore.mix.titaniumchrome",
                            "ore.mix.platinumchrome",
                            "ore.mix.iridiummytryl",
                            "ore.mix.draconium",
                            "ore.mix.heavypentele",
                            "ore.small.bismuth",
                            "ore.small.chromite",
                            "ore.small.deepiron",
                            "ore.small.desh",
                            "ore.small.lead",
                            "ore.small.ledox",
                            "ore.small.naquadah",
                            "ore.small.nickel",
                            "ore.small.oriharukon",
                            "ore.small.redstone",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.VENUS,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.723 * EARTH_RADIUS_TO_AU, 0.00022, seededPhase("venus"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(737)
                        .radiation(0.22)
                        .oreProfile("gtnh_t4")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.cassiterite",
                            "ore.mix.tetrahedrite",
                            "ore.mix.sulfur",
                            "ore.mix.redstone",
                            "ore.mix.nickel",
                            "ore.mix.pitchblende",
                            "ore.mix.monazite",
                            "ore.mix.galena",
                            "ore.mix.beryllium",
                            "ore.mix.iridiummytryl",
                            "ore.mix.quantium",
                            "ore.mix.quartzspace",
                            "ore.mix.rutile",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.draconium",
                            "ore.small.firestone",
                            "ore.small.gold",
                            "ore.small.lead",
                            "ore.small.meteoriciron",
                            "ore.small.mythril",
                            "ore.small.naquadah",
                            "ore.small.nickel",
                            "ore.small.saltpeter",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.tungsten")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            DimensionEnum.MARS,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(1.524 * EARTH_RADIUS_TO_AU, 0.00011, seededPhase("mars"))
                .texture(EnumTextures.ICON_MARS.get())
                .spriteSize(0.825)
                .properties(
                    b -> b.orbitalGravity(42828.375816 * GRAVITY_SCALE, 0.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .localGravityG(0.25)
                        .massEarthRelative(0.25)
                        .orbitalRadiusEarthRelative(1.52 * EARTH_RADIUS_TO_AU)
                        .radiusEarthRelative(0.53)
                        .temperature(67)
                        .radiation(0.10)
                        .oreProfile("gtnh_t2")
                        .gtOreDepositIds(
                            "ore.mix.gold",
                            "ore.mix.iron",
                            "ore.mix.tetrahedrite",
                            "ore.mix.sulfur",
                            "ore.mix.salts",
                            "ore.mix.redstone",
                            "ore.mix.nickel",
                            "ore.mix.pitchblende",
                            "ore.mix.tungstate",
                            "ore.mix.galena",
                            "ore.mix.beryllium",
                            "ore.mix.desh",
                            "ore.mix.heavypentele",
                            "ore.mix.quartzspace",
                            "ore.small.bismuth",
                            "ore.small.chromite",
                            "ore.small.copper",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.lead",
                            "ore.small.meteoriciron",
                            "ore.small.nickel",
                            "ore.small.oriharukon",
                            "ore.small.redstone",
                            "ore.small.saltpeter",
                            "ore.small.tin",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.zinc")
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
            CelestialObjectId.PHOBOS,
            builder -> builder.parent(CelestialObjectId.MARS)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(9375.0 / EARTH_RADIUS_KM, 0.00110, seededPhase("phobos"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.035)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(233)
                        .radiation(0.20)
                        .oreProfile("gtnh_t2")
                        .gtOreDepositIds(
                            "ore.mix.gold",
                            "ore.mix.sulfur",
                            "ore.mix.bauxite",
                            "ore.mix.nickel",
                            "ore.mix.pitchblende",
                            "ore.mix.molybdenum",
                            "ore.mix.diamond",
                            "ore.mix.uranium",
                            "ore.mix.draconium",
                            "ore.mix.oriharukon",
                            "ore.mix.heavypentele",
                            "ore.mix.quartzspace",
                            "ore.small.chromite",
                            "ore.small.copper",
                            "ore.small.desh",
                            "ore.small.draconium",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.lapis",
                            "ore.small.meteoriciron",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.DEIMOS,
            builder -> builder.parent(CelestialObjectId.MARS)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(23457.0 / EARTH_RADIUS_KM, 0.00080, seededPhase("deimos"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.035)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(233)
                        .radiation(0.20)
                        .oreProfile("gtnh_t2")
                        .gtOreDepositIds(
                            "ore.mix.magnetite",
                            "ore.mix.tetrahedrite",
                            "ore.mix.sulfur",
                            "ore.mix.nickel",
                            "ore.mix.monazite",
                            "ore.mix.tungstate",
                            "ore.mix.lapis",
                            "ore.mix.uranium",
                            "ore.mix.draconium",
                            "ore.mix.oriharukon",
                            "ore.small.chromite",
                            "ore.small.desh",
                            "ore.small.diamond",
                            "ore.small.draconium",
                            "ore.small.lead",
                            "ore.small.meteoriciron",
                            "ore.small.nickel",
                            "ore.small.saltpeter",
                            "ore.small.tin",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.OVERWORLD,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(EARTH_RADIUS_TO_AU, 0.00022, seededPhase("overworld"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.orbitalGravity(398600.435507 * GRAVITY_SCALE, 0.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .localGravityG(1.0)
                        .orbitalRadiusEarthRelative(EARTH_RADIUS_TO_AU)
                        .surfacePressurePa(1.0)
                        .addAtmosphereIngredient(requiredGas(Materials.Air), 1.0)
                        .temperature(288)
                        .radiation(0.00)
                        .oreProfile("gtnh_overworld")
                        .gtOreDepositIds(
                            "ore.mix.lignite",
                            "ore.mix.coal",
                            "ore.mix.magnetite",
                            "ore.mix.gold",
                            "ore.mix.iron",
                            "ore.mix.cassiterite",
                            "ore.mix.copper",
                            "ore.mix.salts",
                            "ore.mix.redstone",
                            "ore.mix.soapstone",
                            "ore.mix.manganese",
                            "ore.mix.diamond",
                            "ore.mix.apatite",
                            "ore.mix.lapis",
                            "ore.mix.oilsand",
                            "ore.mix.coppertin",
                            "ore.mix.mineralsand",
                            "ore.mix.garnettin",
                            "ore.mix.kaolinitezeolite",
                            "ore.mix.mica",
                            "ore.mix.dolomite",
                            "ore.small.coal",
                            "ore.small.copper",
                            "ore.small.diamond",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.lapis",
                            "ore.small.nickel",
                            "ore.small.redstone",
                            "ore.small.silver",
                            "ore.small.tin",
                            "ore.small.zinc")
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
            DimensionEnum.MOON,
            builder -> builder.parent(CelestialObjectId.OVERWORLD)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(384400.0 / EARTH_RADIUS_KM, 0.00145, seededPhase("moon"))
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
                        .oreProfile("gtnh_t1")
                        .gtOreDepositIds(
                            "ore.mix.cassiterite",
                            "ore.mix.copper",
                            "ore.mix.bauxite",
                            "ore.mix.monazite",
                            "ore.mix.molybdenum",
                            "ore.mix.galena",
                            "ore.mix.titaniumchrome",
                            "ore.mix.quartzspace",
                            "ore.small.meteoriciron")
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
            CelestialObjectId.CERES,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(2.767 * EARTH_RADIUS_TO_AU, 0.00008, seededPhase("ceres"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.10)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(168)
                        .radiation(0.18)
                        .oreProfile("gtnh_t3")
                        .gtOreDepositIds(
                            "ore.mix.magnetite",
                            "ore.mix.iron",
                            "ore.mix.copper",
                            "ore.mix.soapstone",
                            "ore.mix.molybdenum",
                            "ore.mix.manganese",
                            "ore.mix.olivine",
                            "ore.mix.lapis",
                            "ore.mix.beryllium",
                            "ore.mix.uranium",
                            "ore.mix.platinumchrome",
                            "ore.mix.saltpeterelectrotine",
                            "ore.mix.richnuclear",
                            "ore.small.bismuth",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.gold",
                            "ore.small.lead",
                            "ore.small.naquadah",
                            "ore.small.nickel",
                            "ore.small.oriharukon",
                            "ore.small.quantium",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.JUPITER,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.GAS_GIANT)
                .circularOrbit(5.204 * EARTH_RADIUS_TO_AU, 0.00005, seededPhase("jupiter"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.54)
                .properties(
                    b -> b.orbitalGravity(126712764.1 * GRAVITY_SCALE, 0.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(165)
                        .radiation(0.45)
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.IO,
            builder -> builder.parent(CelestialObjectId.JUPITER)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(421800.0 / EARTH_RADIUS_KM, 0.00100, seededPhase("io"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.08)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(130)
                        .radiation(0.36)
                        .oreProfile("gtnh_t4")
                        .gtOreDepositIds(
                            "ore.mix.magnetite",
                            "ore.mix.cassiterite",
                            "ore.mix.sulfur",
                            "ore.mix.pitchblende",
                            "ore.mix.monazite",
                            "ore.mix.manganese",
                            "ore.mix.platinumchrome",
                            "ore.mix.iridiummytryl",
                            "ore.mix.mytryl",
                            "ore.mix.infusedgold",
                            "ore.mix.richnuclear",
                            "ore.mix.quartzspace",
                            "ore.mix.luvtantalite",
                            "ore.small.bismuth",
                            "ore.small.chromite",
                            "ore.small.firestone",
                            "ore.small.iron",
                            "ore.small.lapis",
                            "ore.small.meteoriciron",
                            "ore.small.naquadah",
                            "ore.small.quantium",
                            "ore.small.redstone",
                            "ore.small.saltpeter",
                            "ore.small.silver",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.tungsten",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.EUROPA,
            builder -> builder.parent(CelestialObjectId.JUPITER)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(671100.0 / EARTH_RADIUS_KM, 0.00080, seededPhase("europa"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.07)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(102)
                        .radiation(0.30)
                        .oreProfile("gtnh_t3")
                        .gtOreDepositIds(
                            "ore.mix.mineralsand",
                            "ore.mix.garnettin",
                            "ore.mix.ledox",
                            "ore.mix.europa",
                            "ore.mix.europacore")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.GANYMEDE,
            builder -> builder.parent(CelestialObjectId.JUPITER)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(1070400.0 / EARTH_RADIUS_KM, 0.00070, seededPhase("ganymede"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.09)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(110)
                        .radiation(0.26)
                        .oreProfile("gtnh_t3")
                        .gtOreDepositIds(
                            "ore.mix.iron",
                            "ore.mix.tetrahedrite",
                            "ore.mix.bauxite",
                            "ore.mix.redstone",
                            "ore.mix.tungstate",
                            "ore.mix.diamond",
                            "ore.mix.galena",
                            "ore.mix.uranium",
                            "ore.mix.titaniumchrome",
                            "ore.mix.platinumchrome",
                            "ore.mix.richnuclear",
                            "ore.small.chromite",
                            "ore.small.draconium",
                            "ore.small.iron",
                            "ore.small.lapis",
                            "ore.small.lead",
                            "ore.small.redstone",
                            "ore.small.saltpeter",
                            "ore.small.tin",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.CALLISTO,
            builder -> builder.parent(CelestialObjectId.JUPITER)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(1882700.0 / EARTH_RADIUS_KM, 0.00055, seededPhase("callisto"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.09)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(134)
                        .radiation(0.22)
                        .oreProfile("gtnh_t3")
                        .gtOreDepositIds(
                            "ore.mix.gold",
                            "ore.mix.iron",
                            "ore.mix.copper",
                            "ore.mix.monazite",
                            "ore.mix.tungstate",
                            "ore.mix.titaniumchrome",
                            "ore.mix.platinumchrome",
                            "ore.mix.callistoice",
                            "ore.mix.richnuclear",
                            "ore.small.bismuth",
                            "ore.small.chromite",
                            "ore.small.desh",
                            "ore.small.diamond",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.ledox",
                            "ore.small.mythril",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.SATURN,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.GAS_GIANT)
                .circularOrbit(9.583 * EARTH_RADIUS_TO_AU, 0.00004, seededPhase("saturn"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.48)
                .properties(
                    b -> b.orbitalGravity(37940584.8418 * GRAVITY_SCALE, 0.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(134)
                        .radiation(0.24)
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.ENCELADUS,
            builder -> builder.parent(CelestialObjectId.SATURN)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(238400.0 / EARTH_RADIUS_KM, 0.00090, seededPhase("enceladus"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.06)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(75)
                        .radiation(0.20)
                        .oreProfile("gtnh_t5")
                        .gtOreDepositIds(
                            "ore.mix.copper",
                            "ore.mix.monazite",
                            "ore.mix.tungstate",
                            "ore.mix.lapis",
                            "ore.mix.uranium",
                            "ore.mix.iridiummytryl",
                            "ore.mix.osmium",
                            "ore.mix.ledox",
                            "ore.mix.vanadiumgold",
                            "ore.small.chromite",
                            "ore.small.iron",
                            "ore.small.lapis",
                            "ore.small.ledox",
                            "ore.small.naquadah",
                            "ore.small.neutronium",
                            "ore.small.saltpeter",
                            "ore.small.silver",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.TITAN,
            builder -> builder.parent(CelestialObjectId.SATURN)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(1221900.0 / EARTH_RADIUS_KM, 0.00060, seededPhase("titan"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.10)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(94)
                        .radiation(0.18)
                        .oreProfile("gtnh_t5")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.bauxite",
                            "ore.mix.nickel",
                            "ore.mix.monazite",
                            "ore.mix.molybdenum",
                            "ore.mix.manganese",
                            "ore.mix.diamond",
                            "ore.mix.beryllium",
                            "ore.mix.titaniumchrome",
                            "ore.mix.iridiummytryl",
                            "ore.mix.osmium",
                            "ore.mix.infusedgold",
                            "ore.mix.heavypentele",
                            "ore.mix.rutile",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.iron",
                            "ore.small.neutronium",
                            "ore.small.oriharukon",
                            "ore.small.quantium",
                            "ore.small.redstone",
                            "ore.small.silver",
                            "ore.small.tin",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.URANUS,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.GAS_GIANT)
                .circularOrbit(19.218 * EARTH_RADIUS_TO_AU, 0.000035, seededPhase("uranus"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.38)
                .properties(
                    b -> b.orbitalGravity(5794556.4 * GRAVITY_SCALE, 0.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(76)
                        .radiation(0.18)
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.MIRANDA,
            builder -> builder.parent(CelestialObjectId.URANUS)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(129846.0 / EARTH_RADIUS_KM, 0.00090, seededPhase("miranda"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.06)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(60)
                        .radiation(0.18)
                        .oreProfile("gtnh_t5")
                        .gtOreDepositIds(
                            "ore.mix.cassiterite",
                            "ore.mix.tetrahedrite",
                            "ore.mix.redstone",
                            "ore.mix.diamond",
                            "ore.mix.titaniumchrome",
                            "ore.mix.iridiummytryl",
                            "ore.mix.osmium",
                            "ore.mix.desh",
                            "ore.mix.draconium",
                            "ore.mix.luvtantalite",
                            "ore.small.desh",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.mythril",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.OBERON,
            builder -> builder.parent(CelestialObjectId.URANUS)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(583511.0 / EARTH_RADIUS_KM, 0.00065, seededPhase("oberon"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.07)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(70)
                        .radiation(0.18)
                        .oreProfile("gtnh_t5")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.iron",
                            "ore.mix.pitchblende",
                            "ore.mix.tungstate",
                            "ore.mix.manganese",
                            "ore.mix.galena",
                            "ore.mix.platinumchrome",
                            "ore.mix.osmium",
                            "ore.mix.tungstenirons",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.draconium",
                            "ore.small.lapis",
                            "ore.small.lead",
                            "ore.small.ledox",
                            "ore.small.neutronium",
                            "ore.small.silver",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.NEPTUNE,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.GAS_GIANT)
                .circularOrbit(30.11 * EARTH_RADIUS_TO_AU, 0.00003, seededPhase("neptune"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.38)
                .properties(
                    b -> b.orbitalGravity(6836527.10058 * GRAVITY_SCALE, 0.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(72)
                        .radiation(0.18)
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.PROTEUS,
            builder -> builder.parent(CelestialObjectId.NEPTUNE)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(117600.0 / EARTH_RADIUS_KM, 0.00080, seededPhase("proteus"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.06)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(51)
                        .radiation(0.18)
                        .oreProfile("gtnh_t6")
                        .gtOreDepositIds(
                            "ore.mix.copper",
                            "ore.mix.bauxite",
                            "ore.mix.molybdenum",
                            "ore.mix.diamond",
                            "ore.mix.uranium",
                            "ore.mix.neutronium",
                            "ore.mix.titaniumchrome",
                            "ore.mix.osmium",
                            "ore.mix.infusedgold",
                            "ore.mix.tungstenirons",
                            "ore.mix.vanadiumgold",
                            "ore.mix.quartzspace",
                            "ore.small.bismuth",
                            "ore.small.chromite",
                            "ore.small.copper",
                            "ore.small.desh",
                            "ore.small.mythril",
                            "ore.small.naquadah",
                            "ore.small.neutronium",
                            "ore.small.redstone",
                            "ore.small.saltpeter",
                            "ore.small.silver",
                            "ore.small.tin",
                            "ore.small.titanium",
                            "ore.small.tungstate",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.TRITON,
            builder -> builder.parent(CelestialObjectId.NEPTUNE)
                .objectClass(CelestialObject.Class.MOON)
                .circularOrbit(354800.0 / EARTH_RADIUS_KM, 0.00055, seededPhase("triton"))
                .texture(EnumTextures.ICON_MOON.get())
                .spriteSize(0.08)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(38)
                        .radiation(0.18)
                        .oreProfile("gtnh_t6")
                        .gtOreDepositIds(
                            "ore.mix.gold",
                            "ore.mix.nickel",
                            "ore.mix.monazite",
                            "ore.mix.tungstate",
                            "ore.mix.manganese",
                            "ore.mix.galena",
                            "ore.mix.neutronium",
                            "ore.mix.iridiummytryl",
                            "ore.mix.niobium",
                            "ore.mix.tungstenirons",
                            "ore.mix.uraniumgtnh",
                            "ore.small.blackplutonium",
                            "ore.small.chromite",
                            "ore.small.copper",
                            "ore.small.desh",
                            "ore.small.diamond",
                            "ore.small.iron",
                            "ore.small.lead",
                            "ore.small.neutronium",
                            "ore.small.oriharukon",
                            "ore.small.silver",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.PLUTO,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(39.482 * EARTH_RADIUS_TO_AU, 0.000025, seededPhase("pluto"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.12)
                .properties(
                    b -> b.orbitalGravity(975.5 * GRAVITY_SCALE, 0.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(44)
                        .radiation(0.22)
                        .oreProfile("gtnh_t7")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.gold",
                            "ore.mix.iron",
                            "ore.mix.bauxite",
                            "ore.mix.molybdenum",
                            "ore.mix.tungstate",
                            "ore.mix.diamond",
                            "ore.mix.beryllium",
                            "ore.mix.neutronium",
                            "ore.mix.titaniumchrome",
                            "ore.mix.platinumchrome",
                            "ore.mix.osmium",
                            "ore.mix.blackplutonium",
                            "ore.mix.tungstenirons",
                            "ore.mix.uraniumgtnh",
                            "ore.small.blackplutonium",
                            "ore.small.chromite",
                            "ore.small.draconium",
                            "ore.small.gold",
                            "ore.small.lead",
                            "ore.small.ledox",
                            "ore.small.meteoriciron",
                            "ore.small.naquadah",
                            "ore.small.neutronium",
                            "ore.small.nickel",
                            "ore.small.quantium",
                            "ore.small.silver",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.KUIPER_BELT,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.ASTEROID_BELT)
                .circularOrbit(43.0 * EARTH_RADIUS_TO_AU, 0.00002, seededPhase("kuiper_belt"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.60)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(40)
                        .radiation(0.28)
                        .oreProfile("gtnh_t7")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.tetrahedrite",
                            "ore.mix.bauxite",
                            "ore.mix.nickel",
                            "ore.mix.pitchblende",
                            "ore.mix.tungstate",
                            "ore.mix.diamond",
                            "ore.mix.uranium",
                            "ore.mix.neutronium",
                            "ore.mix.platinumchrome",
                            "ore.mix.iridiummytryl",
                            "ore.mix.osmium",
                            "ore.mix.carbonice",
                            "ore.mix.hhoice",
                            "ore.mix.hydrocarbonice",
                            "ore.mix.nitrogenice",
                            "ore.mix.sulfurice",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.lead",
                            "ore.small.naquadah",
                            "ore.small.neutronium",
                            "ore.small.nickel",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.HAUMEA,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(43.218 * EARTH_RADIUS_TO_AU, 0.00002, seededPhase("haumea"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.11)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(44)
                        .radiation(0.26)
                        .oreProfile("gtnh_t7")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.bauxite",
                            "ore.mix.pitchblende",
                            "ore.mix.monazite",
                            "ore.mix.tungstate",
                            "ore.mix.olivine",
                            "ore.mix.beryllium",
                            "ore.mix.uranium",
                            "ore.mix.neutronium",
                            "ore.mix.infusedgold",
                            "ore.mix.uraniumgtnh",
                            "ore.mix.netherstar",
                            "ore.small.blackplutonium",
                            "ore.small.chromite",
                            "ore.small.desh",
                            "ore.small.draconium",
                            "ore.small.iron",
                            "ore.small.ledox",
                            "ore.small.neutronium",
                            "ore.small.oriharukon",
                            "ore.small.quantium",
                            "ore.small.titanium",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.MAKEMAKE,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(45.715 * EARTH_RADIUS_TO_AU, 0.00002, seededPhase("makemake"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.11)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(40)
                        .radiation(0.26)
                        .oreProfile("gtnh_t7")
                        .gtOreDepositIds(
                            "ore.mix.magnetite",
                            "ore.mix.bauxite",
                            "ore.mix.pitchblende",
                            "ore.mix.monazite",
                            "ore.mix.tungstate",
                            "ore.mix.olivine",
                            "ore.mix.beryllium",
                            "ore.mix.uranium",
                            "ore.mix.neutronium",
                            "ore.mix.blackplutonium",
                            "ore.mix.niobium",
                            "ore.mix.vanadiumgold",
                            "ore.mix.garnet",
                            "ore.small.bismuth",
                            "ore.small.blackplutonium",
                            "ore.small.chromite",
                            "ore.small.desh",
                            "ore.small.draconium",
                            "ore.small.lead",
                            "ore.small.mythril",
                            "ore.small.neutronium",
                            "ore.small.nickel",
                            "ore.small.oriharukon",
                            "ore.small.quantium",
                            "ore.small.titanium")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.BARNARD_C,
            builder -> builder.parent(CelestialObjectId.BARNARDA)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.75 * EARTH_RADIUS_TO_AU, 0.00019, seededPhase("barnard_c"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.16)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(190)
                        .radiation(0.32)
                        .oreProfile("gtnh_t8")
                        .gtOreDepositIds(
                            "ore.mix.lignite",
                            "ore.mix.salts",
                            "ore.mix.oilsand",
                            "ore.mix.mineralsand",
                            "ore.mix.mica",
                            "ore.mix.osmium",
                            "ore.mix.blackplutonium",
                            "ore.mix.tungstenirons",
                            "ore.mix.heavypentele",
                            "ore.mix.secondlanthanid")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.BARNARD_E,
            builder -> builder.parent(CelestialObjectId.BARNARDA)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(2.0 * EARTH_RADIUS_TO_AU, 0.00008, seededPhase("barnard_e"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(140)
                        .radiation(0.36)
                        .oreProfile("gtnh_t8")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.magnetite",
                            "ore.mix.copper",
                            "ore.mix.molybdenum",
                            "ore.mix.manganese",
                            "ore.mix.olivine",
                            "ore.mix.uranium",
                            "ore.mix.neutronium",
                            "ore.mix.infusedgold",
                            "ore.mix.tungstenirons",
                            "ore.mix.uraniumgtnh",
                            "ore.mix.netherstar",
                            "ore.mix.rareearth",
                            "ore.small.awdraconium",
                            "ore.small.blackplutonium",
                            "ore.small.iron",
                            "ore.small.naquadah",
                            "ore.small.neutronium",
                            "ore.small.nickel",
                            "ore.small.titanium",
                            "ore.small.zinc")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.BARNARD_F,
            builder -> builder.parent(CelestialObjectId.BARNARDA)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(2.5 * EARTH_RADIUS_TO_AU, 0.00006, seededPhase("barnard_f"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(110)
                        .radiation(0.40)
                        .oreProfile("gtnh_t8")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.gold",
                            "ore.mix.copper",
                            "ore.mix.redstone",
                            "ore.mix.pitchblende",
                            "ore.mix.monazite",
                            "ore.mix.manganese",
                            "ore.mix.diamond",
                            "ore.mix.beryllium",
                            "ore.mix.neutronium",
                            "ore.mix.niobium",
                            "ore.mix.tungstenirons",
                            "ore.mix.uraniumgtnh",
                            "ore.mix.vanadiumgold",
                            "ore.mix.garnet",
                            "ore.mix.rareearth",
                            "ore.small.awdraconium",
                            "ore.small.bedrockium",
                            "ore.small.blackplutonium",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.naquadah",
                            "ore.small.neutronium",
                            "ore.small.nickel",
                            "ore.small.realgar",
                            "ore.small.titanium")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.CENTAURI_BB,
            builder -> builder.parent(CelestialObjectId.ALPHA_CENTAURI_A)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.9 * EARTH_RADIUS_TO_AU, 0.00018, seededPhase("centauri_bb"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.17)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(320)
                        .radiation(0.34)
                        .oreProfile("gtnh_t8")
                        .gtOreDepositIds(
                            "ore.mix.tetrahedrite",
                            "ore.mix.netherquartz",
                            "ore.mix.sulfur",
                            "ore.mix.redstone",
                            "ore.mix.pitchblende",
                            "ore.mix.manganese",
                            "ore.mix.beryllium",
                            "ore.mix.garnettin",
                            "ore.mix.saltpeterelectrotine",
                            "ore.mix.blackplutonium",
                            "ore.mix.rareearth",
                            "ore.mix.secondlanthanid",
                            "ore.mix.quartzspace")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.TCETI_E,
            builder -> builder.parent(CelestialObjectId.TCETI_A)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(1.25 * EARTH_RADIUS_TO_AU, 0.00014, seededPhase("tceti_e"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.17)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(270)
                        .radiation(0.30)
                        .oreProfile("gtnh_t8")
                        .gtOreDepositIds(
                            "ore.mix.magnetite",
                            "ore.mix.gold",
                            "ore.mix.cassiterite",
                            "ore.mix.bauxite",
                            "ore.mix.salts",
                            "ore.mix.apatite",
                            "ore.mix.oilsand",
                            "ore.mix.titaniumchrome",
                            "ore.mix.kaolinitezeolite",
                            "ore.mix.blackplutonium",
                            "ore.mix.netherstar",
                            "ore.mix.europa",
                            "ore.mix.europacore",
                            "ore.mix.quartzspace",
                            "ore.small.awdraconium",
                            "ore.small.lapis",
                            "ore.small.neutronium")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.VEGA_B,
            builder -> builder.parent(CelestialObjectId.VEGA)
                .objectClass(CelestialObject.Class.PLANET)
                .circularOrbit(0.5 * EARTH_RADIUS_TO_AU, 0.00020, seededPhase("vega_b"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.17)
                .properties(
                    b -> b.visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(360)
                        .radiation(0.44)
                        .oreProfile("gtnh_t8")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.gold",
                            "ore.mix.tetrahedrite",
                            "ore.mix.redstone",
                            "ore.mix.pitchblende",
                            "ore.mix.tungstate",
                            "ore.mix.galena",
                            "ore.mix.lapis",
                            "ore.mix.neutronium",
                            "ore.mix.infusedgold",
                            "ore.mix.niobium",
                            "ore.mix.uraniumgtnh",
                            "ore.mix.vanadiumgold",
                            "ore.mix.netherstar",
                            "ore.mix.garnet",
                            "ore.mix.rareearth",
                            "ore.mix.heavypentele",
                            "ore.small.awdraconium",
                            "ore.small.bismuth",
                            "ore.small.blackplutonium",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.infinitycatalyst",
                            "ore.small.lead",
                            "ore.small.neutronium",
                            "ore.small.silver")
                        .metadata("source", "gtnh_galaxyspace")));

        register(
            CelestialObjectId.FROZEN_BELT,
            builder -> builder.parent(CelestialObjectId.SOL)
                .objectClass(CelestialObject.Class.ASTEROID_BELT)
                .circularOrbit(2.8 * EARTH_RADIUS_TO_AU, 0.00005, seededPhase("frozen_belt"))
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
                        .oreProfile("gtnh_t3")
                        .gtOreDepositIds(
                            "ore.mix.naquadah",
                            "ore.mix.gold",
                            "ore.mix.tetrahedrite",
                            "ore.mix.bauxite",
                            "ore.mix.platinum",
                            "ore.mix.tungstate",
                            "ore.mix.titaniumchrome",
                            "ore.mix.platinumchrome",
                            "ore.mix.carbonice",
                            "ore.mix.hhoice",
                            "ore.mix.hydrocarbonice",
                            "ore.mix.nitrogenice",
                            "ore.mix.sulfurice",
                            "ore.small.chromite",
                            "ore.small.diamond",
                            "ore.small.gold",
                            "ore.small.iron",
                            "ore.small.lead",
                            "ore.small.nickel",
                            "ore.small.titanium",
                            "ore.small.tungstate")
                        .asteroidFieldProfile(frozenBeltAsteroidFieldProfile())
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

        registerRemainingGtnhBodies();
    }

    // Catalog sources: GalaxySpace's SolarSystem/ACentauri/Barnards/TCeti/Vega registrations,
    // BartWorks Ross128SolarSystem and AmunRa's celestial registrations.
    // Fictional/extrasolar distances retain the source map spacing, not measured astronomical distances.
    // Amun-Ra fictional moon distances retain their relative ordering in map units.
    private static void registerRemainingGtnhBodies() {
        registerMapStar(CelestialObjectId.ROSS_128, -1500.0, -6500.0);
        registerMapStar(CelestialObjectId.RA, 0.0, 8000.0);
        registerMapBody(
            CelestialObjectId.MIMAS,
            CelestialObjectId.SATURN,
            CelestialObject.Class.MOON,
            186000.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.TETHYS,
            CelestialObjectId.SATURN,
            CelestialObject.Class.MOON,
            295000.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.DIONE,
            CelestialObjectId.SATURN,
            CelestialObject.Class.MOON,
            377700.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.RHEA,
            CelestialObjectId.SATURN,
            CelestialObject.Class.MOON,
            527200.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.IAPETUS,
            CelestialObjectId.SATURN,
            CelestialObject.Class.MOON,
            3561700.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.ARIEL,
            CelestialObjectId.URANUS,
            CelestialObject.Class.MOON,
            190929.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.UMBRIEL,
            CelestialObjectId.URANUS,
            CelestialObject.Class.MOON,
            265986.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.TITANIA,
            CelestialObjectId.URANUS,
            CelestialObject.Class.MOON,
            436298.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.CHARON,
            CelestialObjectId.PLUTO,
            CelestialObject.Class.MOON,
            19600.0 / EARTH_RADIUS_KM);
        registerMapBody(
            CelestialObjectId.ERIS,
            CelestialObjectId.SOL,
            CelestialObject.Class.PLANET,
            67.781 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.ALPHA_CENTAURI_B,
            CelestialObjectId.ALPHA_CENTAURI_A,
            CelestialObject.Class.STAR,
            0.3 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.BARNARD_B,
            CelestialObjectId.BARNARDA,
            CelestialObject.Class.PLANET,
            0.5 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.BARNARD_D,
            CelestialObjectId.BARNARDA,
            CelestialObject.Class.PLANET,
            1.5 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.BARNARD_G,
            CelestialObjectId.BARNARDA,
            CelestialObject.Class.PLANET,
            2.75 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.BARNARD_BELT,
            CelestialObjectId.BARNARDA,
            CelestialObject.Class.ASTEROID_BELT,
            3.25 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.TCETI_B,
            CelestialObjectId.TCETI_A,
            CelestialObject.Class.PLANET,
            0.5 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.TCETI_C,
            CelestialObjectId.TCETI_A,
            CelestialObject.Class.PLANET,
            0.75 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.TCETI_D,
            CelestialObjectId.TCETI_A,
            CelestialObject.Class.PLANET,
            1 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.TCETI_F,
            CelestialObjectId.TCETI_A,
            CelestialObject.Class.PLANET,
            1.5 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.TCETI_G,
            CelestialObjectId.TCETI_A,
            CelestialObject.Class.PLANET,
            1.75 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.TCETI_BELT,
            CelestialObjectId.TCETI_A,
            CelestialObject.Class.ASTEROID_BELT,
            2 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.VEGA_C,
            CelestialObjectId.VEGA,
            CelestialObject.Class.PLANET,
            1.75 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.VEGA_INNER_BELT,
            CelestialObjectId.VEGA,
            CelestialObject.Class.ASTEROID_BELT,
            1.6 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.VEGA_OUTER_BELT,
            CelestialObjectId.VEGA,
            CelestialObject.Class.ASTEROID_BELT,
            2.5 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.ROSS_128_B,
            CelestialObjectId.ROSS_128,
            CelestialObject.Class.PLANET,
            0.75 * EARTH_RADIUS_TO_AU,
            "ore.mix.ross128.Thorianit",
            "ore.mix.ross128.carbon",
            "ore.mix.ross128.bismuth",
            "ore.mix.ross128.TurmalinAlkali",
            "ore.mix.ross128.Roquesit",
            "ore.mix.ross128.Tungstate",
            "ore.mix.ross128.CopperSulfits",
            "ore.mix.ross128.Forsterit",
            "ore.mix.ross128.Hedenbergit",
            "ore.mix.ross128.RedZircon");
        registerMapBody(
            CelestialObjectId.ROSS_128_BA,
            CelestialObjectId.ROSS_128_B,
            CelestialObject.Class.MOON,
            10.0,
            "ore.mix.ross128ba.tib",
            "ore.mix.ross128ba.Tungstate",
            "ore.mix.ross128ba.bart",
            "ore.mix.ross128ba.TurmalinAlkali",
            "ore.mix.ross128ba.Amethyst",
            "ore.mix.ross128ba.CopperSulfits",
            "ore.mix.ross128ba.RedZircon",
            "ore.mix.ross128ba.Fluorspar");
        registerMapBody(
            CelestialObjectId.AMUN,
            CelestialObjectId.RA,
            CelestialObject.Class.STAR,
            0.7 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.OSIRIS,
            CelestialObjectId.RA,
            CelestialObject.Class.PLANET,
            0.34 * EARTH_RADIUS_TO_AU);
        registerMapBody(
            CelestialObjectId.HORUS,
            CelestialObjectId.RA,
            CelestialObject.Class.PLANET,
            0.55 * EARTH_RADIUS_TO_AU,
            "ore.mix.sapphire",
            "ore.mix.draconium",
            "ore.mix.quantium",
            "ore.mix.mytryl",
            "ore.mix.ledox",
            "ore.mix.oriharukon",
            "ore.mix.blackplutonium",
            "ore.mix.netherstar",
            "ore.mix.garnet",
            "ore.mix.europa",
            "ore.mix.quartzspace",
            "ore.mix.certusquartz",
            "ore.mix.cosmicneutronium",
            "ore.small.amethyst",
            "ore.small.bluetopaz",
            "ore.small.certusquartz",
            "ore.small.chargedcertus",
            "ore.small.emerald",
            "ore.small.foolsruby",
            "ore.small.garnetred",
            "ore.small.garnetyellow",
            "ore.small.greensapphire",
            "ore.small.jade",
            "ore.small.jasper",
            "ore.small.mythril",
            "ore.small.olivine",
            "ore.small.opal",
            "ore.small.ruby",
            "ore.small.sapphire",
            "ore.small.tanzanite",
            "ore.small.topaz");
        registerMapBody(
            CelestialObjectId.BAAL,
            CelestialObjectId.RA,
            CelestialObject.Class.GAS_GIANT,
            1.2 * EARTH_RADIUS_TO_AU);
        registerMapBody(CelestialObjectId.BAAL_RINGS, CelestialObjectId.BAAL, CelestialObject.Class.ASTEROID_BELT, 9);
        registerMapBody(CelestialObjectId.KHONSU, CelestialObjectId.BAAL, CelestialObject.Class.MOON, 12.45);
        registerMapBody(
            CelestialObjectId.NEPER,
            CelestialObjectId.BAAL,
            CelestialObject.Class.MOON,
            14.9,
            "ore.mix.netherquartz",
            "ore.mix.quartz",
            "ore.mix.aquaignis",
            "ore.mix.terraaer",
            "ore.mix.perditioordo",
            "ore.mix.kaolinitezeolite",
            "ore.mix.mica",
            "ore.mix.dolomite",
            "ore.mix.tungstenirons",
            "ore.mix.heavypentele",
            "ore.mix.certusquartz",
            "ore.mix.dilithium");
        registerMapBody(CelestialObjectId.IAH, CelestialObjectId.BAAL, CelestialObject.Class.MOON, 18.5);
        registerMapBody(
            CelestialObjectId.MEHEN_BELT,
            CelestialObjectId.RA,
            CelestialObject.Class.ASTEROID_BELT,
            1.4 * EARTH_RADIUS_TO_AU,
            "ore.mix.cassiterite",
            "ore.mix.nickel",
            "ore.mix.platinum",
            "ore.mix.olivine",
            "ore.mix.lapis",
            "ore.mix.uranium",
            "ore.mix.neutronium",
            "ore.mix.titaniumchrome",
            "ore.mix.platinumchrome",
            "ore.mix.iridiummytryl",
            "ore.mix.osmium",
            "ore.mix.awakeneddraconium",
            "ore.small.chromite",
            "ore.small.lapis",
            "ore.small.neutronium",
            "ore.small.nickel",
            "ore.small.olivine",
            "ore.small.tin",
            "ore.small.titanium");
        registerMapBody(
            CelestialObjectId.SEKHMET,
            CelestialObjectId.RA,
            CelestialObject.Class.GAS_GIANT,
            1.6 * EARTH_RADIUS_TO_AU);
        registerMapBody(CelestialObjectId.BAST, CelestialObjectId.SEKHMET, CelestialObject.Class.MOON, 9.8);
        registerMapBody(
            CelestialObjectId.MAAHES,
            CelestialObjectId.SEKHMET,
            CelestialObject.Class.MOON,
            11.4,
            "ore.mix.naquadah",
            "ore.mix.soapstone",
            "ore.mix.platinum",
            "ore.mix.neutronium",
            "ore.mix.mineralsand",
            "ore.mix.quantium",
            "ore.mix.callistoice",
            "ore.mix.vanadiumgold",
            "ore.mix.europacore",
            "ore.mix.tfgalena",
            "ore.mix.naquadria");
        registerMapBody(CelestialObjectId.THOTH, CelestialObjectId.SEKHMET, CelestialObject.Class.MOON, 15.5);
        registerMapBody(
            CelestialObjectId.SETH,
            CelestialObjectId.SEKHMET,
            CelestialObject.Class.MOON,
            17.98,
            "ore.mix.magnetite",
            "ore.mix.gold",
            "ore.mix.cassiterite",
            "ore.mix.bauxite",
            "ore.mix.monazite",
            "ore.mix.platinumchrome",
            "ore.mix.iridiummytryl",
            "ore.mix.osmium",
            "ore.mix.draconium",
            "ore.mix.secondlanthanid",
            "ore.mix.tengam",
            "ore.small.awdraconium",
            "ore.small.draconium");
        registerMapBody(
            CelestialObjectId.ANUBIS,
            CelestialObjectId.RA,
            CelestialObject.Class.PLANET,
            1.9 * EARTH_RADIUS_TO_AU,
            "ore.mix.sulfur",
            "ore.mix.soapstone",
            "ore.mix.olivine",
            "ore.mix.mineralsand",
            "ore.mix.mica",
            "ore.mix.dolomite",
            "ore.mix.desh",
            "ore.mix.callistoice",
            "ore.mix.mytryl",
            "ore.mix.rutile",
            "ore.mix.tfgalena",
            "ore.mix.infinitycatalyst",
            "ore.small.infinitycatalyst");
        registerMapBody(CelestialObjectId.KEBE, CelestialObjectId.ANUBIS, CelestialObject.Class.MOON, 19);
    }

    private static void registerMapStar(CelestialObjectId id, double x, double y) {
        register(
            id,
            builder -> builder.parent(CelestialObjectId.NOVUM_CAELUM)
                .objectClass(CelestialObject.Class.STAR)
                .absolutePosition(x, y)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(1.0)
                .properties(
                    b -> b.orbitalGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("source", "gtnh_map_scaffold")));
    }

    private static void registerMapBody(CelestialObjectId id, CelestialObjectId parent,
        CelestialObject.Class objectClass, double orbitalRadius, String... oreVeinIds) {
        register(
            id,
            builder -> builder.parent(parent)
                .objectClass(objectClass)
                .circularOrbit(orbitalRadius, 0.0001, seededPhase(id.name()))
                .texture(
                    objectClass == CelestialObject.Class.MOON ? EnumTextures.ICON_MOON.get()
                        : EnumTextures.ICON_EGORA.get())
                .spriteSize(objectClass == CelestialObject.Class.MOON ? 0.06 : 0.24)
                .properties(
                    b -> b.visitable(false)
                        .oreProfile(
                            oreVeinIds.length == 0 ? "undefined"
                                : "gtnh_" + id.name()
                                    .toLowerCase(Locale.ROOT))
                        .gtOreDepositIds(oreVeinIds)
                        .canCreateStation(objectClass != CelestialObject.Class.STAR)
                        .canCreateOutpost(
                            objectClass == CelestialObject.Class.PLANET || objectClass == CelestialObject.Class.MOON)
                        .metadata("source", "gtnh_map_scaffold")));
    }

    private static void configureOverworldProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .name(DimensionEnum.OVERWORLD);
    }

    private static AsteroidFieldProfile frozenBeltAsteroidFieldProfile() {
        AsteroidFieldProfile.Builder builder = AsteroidFieldProfile.builder();
        GeneratedAsteroids.register(builder);
        LoreAsteroids.register(builder);
        return builder.build();
    }

    public static void register(CelestialObjectId id, @Nonnull Consumer<CelestialObject.Builder> registrationBuilder) {
        CelestialObject.Builder builder = CelestialObject.builder()
            .key(id);

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
        validateRegistration(registration);
        REGISTRATIONS.put(registration.key(), registration);
        if (registration.dimensionEnum() != null) {
            IDS_BY_DIMENSION.put(registration.dimensionEnum(), registration.key());
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
        return id == null ? Optional.empty() : get(CelestialObjectKey.registered(id));
    }

    public static Optional<CelestialObject> get(CelestialObjectKey key) {
        registerDefaults();
        CelestialObject registered = REGISTRATIONS.get(key);
        if (registered != null) return Optional.of(registered);
        return resolveDynamicMinorBody(key);
    }

    private static Optional<CelestialObject> resolveDynamicMinorBody(CelestialObjectKey key) {
        if (key == null || !key.isMinorBody()) return Optional.empty();
        return AsteroidCelestialMaterializer.resolveMinorBody(
            key,
            beltId -> Optional.ofNullable(REGISTRATIONS.get(CelestialObjectKey.registered(beltId))));
    }

    public static List<CelestialObject> getAll() {
        registerDefaults();
        return Collections.unmodifiableList(new ArrayList<>(REGISTRATIONS.values()));
    }

    public static Map<CelestialObjectKey, CelestialObject> getAllBodies() {
        registerDefaults();
        return Collections.unmodifiableMap(REGISTRATIONS);
    }

    public static List<CelestialObject> getPlayableBodies() {
        registerDefaults();
        return REGISTRATIONS.values()
            .stream()
            .filter(body -> body.playableDimensionProfile() != null)
            .toList();
    }

    /**
     * Returns registered hierarchy children only (planets, moons, the belt body itself, …).
     * <p>
     * Does <b>not</b> include generated asteroid-field minors. Those come from
     * {@code children(parent, discoveryView, includeHidden)} (or the client equivalent).
     */
    public static List<CelestialObject> getChildren(CelestialObjectKey parentKey) {
        registerDefaults();
        if (parentKey == null) return List.of();
        return Collections.unmodifiableList(
            hierarchy().childrenByParentId()
                .getOrDefault(parentKey, List.of()));
    }

    /**
     * Sole view-aware owner of a complete child list for a parent.
     * Composes registered hierarchy children first, then materialized minor bodies
     * visible under {@code discoveryView} (or all minors when {@code includeHidden}).
     * <p>
     * Clients must pass a synced discovery view and must not rebuild a second child
     * list from asteroid projection code.
     *
     * @apiNote Blind hierarchy walks stay on {@link #getChildren(CelestialObjectKey)}.
     */
    public static List<CelestialObject> children(CelestialObjectKey parentKey, CelestialDiscoveryView discoveryView,
        boolean includeHidden) {
        registerDefaults();
        if (parentKey == null) return List.of();
        List<CelestialObject> registered = getChildren(parentKey);
        List<CelestialObject> minors = AsteroidCelestialMaterializer.knownChildren(
            parentKey,
            discoveryView == null ? CelestialDiscoveryView.empty() : discoveryView,
            includeHidden,
            beltId -> Optional.ofNullable(REGISTRATIONS.get(CelestialObjectKey.registered(beltId))));
        if (minors.isEmpty()) return registered;
        List<CelestialObject> combined = new ArrayList<>(registered.size() + minors.size());
        combined.addAll(registered);
        combined.addAll(minors);
        return Collections.unmodifiableList(combined);
    }

    /**
     * Definition-default team knowledge for any celestial key.
     * <p>
     * TLDR: registered bodies are born {@code DISCOVERED}/{@code UNKNOWN} and sit
     * outside the scan queue; minor bodies delegate to their asteroid content node
     * for initial detection and ore knowledge. Unresolvable keys fail loudly so
     * {@link com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService}
     * cannot invent knowledge for bodies the registry cannot materialize.
     */
    public static CelestialKnowledgeFacts initialKnowledge(CelestialObjectKey key) {
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        registerDefaults();
        if (key.isRegistered()) {
            if (!REGISTRATIONS.containsKey(key)) {
                throw new IllegalStateException("Unknown celestial object: " + key);
            }
            return CelestialKnowledgeFacts.discoveredUnknown();
        }
        return AsteroidCelestialMaterializer
            .initialKnowledge(
                key,
                beltId -> Optional.ofNullable(REGISTRATIONS.get(CelestialObjectKey.registered(beltId))))
            .orElseThrow(() -> new IllegalStateException("Unknown celestial object: " + key));
    }

    public static List<CelestialObject> getRoots() {
        return hierarchy().roots();
    }

    public static CelestialObject getPrimaryRoot() {
        List<CelestialObject> roots = getRoots();
        if (roots.isEmpty()) throw new IllegalStateException("No celestial objects have been registered");
        return roots.get(0);
    }

    public static Optional<CelestialObject> findByDimension(DimensionEnum dimension) {
        registerDefaults();
        CelestialObjectKey objectKey = IDS_BY_DIMENSION.get(dimension);
        if (objectKey == null) return Optional.empty();
        return Optional.ofNullable(
            hierarchy().bodiesById()
                .get(objectKey));
    }

    private static void validateRegistration(CelestialObject registration) {
        if (REGISTRATIONS.containsKey(registration.key())) {
            throw new IllegalArgumentException("Duplicate celestial object id: " + registration.key());
        }
        if (registration.parentKey() != null && registration.parentKey()
            .equals(registration.key())) {
            throw new IllegalArgumentException("Celestial object cannot orbit itself: " + registration.key());
        }
        if (registration.parentKey() != null && !REGISTRATIONS.containsKey(registration.parentKey())) {
            throw new IllegalArgumentException("Unknown parent celestial object id: " + registration.parentKey());
        }
        if (registration.dimensionEnum() != null) {
            // A dimension can only have one owning celestial key. This keeps
            // dynamic/minor bodies from accidentally stealing a planet dimension.
            if (IDS_BY_DIMENSION.containsKey(registration.dimensionEnum())) {
                throw new IllegalArgumentException("Duplicate dimension mapping for " + registration.dimensionEnum());
            }
        }
    }

    private static void assertMutable() {
        if (frozen) throw new IllegalStateException("Celestial registry is frozen and can no longer be modified");
    }
}
