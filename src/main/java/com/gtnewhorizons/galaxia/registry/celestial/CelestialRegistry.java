package com.gtnewhorizons.galaxia.registry.celestial;

import static com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet.earthRadiusToAU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class CelestialRegistry {

    private static final Map<String, CelestialObjectRegistration> REGISTRATIONS = new LinkedHashMap<>();
    private static final Map<DimensionEnum, String> IDS_BY_DIMENSION = new EnumMap<>(DimensionEnum.class);

    private static boolean bootstrapped;
    private static List<OrbitalCelestialBody> cachedRoots;

    private CelestialRegistry() {}

    private static double seededPhase(String id) {
        long hash = Objects.requireNonNull(id, "id")
            .hashCode() & 0xFFFFFFFFL;
        return (hash / (double) 0xFFFFFFFFL) * Math.PI * 2.0;
    }

    // TODO: Replace these placeholder vanilla ore tables with GT5U ore definitions once the GregTech ore layer is
    // designed.
    private static CelestialBodyProperties.Builder withVanillaOres(CelestialBodyProperties.Builder builder,
        Block... ores) {
        for (Block ore : ores) {
            builder.ore(new ItemStack(ore));
        }
        // gregtech/api ore list goes here later
        return builder;
    }

    private static GtOreVeinDefinition gtVein(String id, String displayName, String primaryOre, String secondaryOre,
        String betweenOre, String sporadicOre, int minY, int maxY, int weight, int density, int size) {
        return new GtOreVeinDefinition(
            id,
            displayName,
            primaryOre,
            secondaryOre,
            betweenOre,
            sporadicOre,
            minY,
            maxY,
            weight,
            density,
            size);
    }

    public static synchronized void registerDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(
            CelestialObjectRegistration.builder()
                .id("novum_caelum")
                .name("Novum Caelum")
                .objectClass(CelestialObjectClass.GALAXY)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("mapLayer", "stars")
                        .build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("vael")
                .name("Vael")
                .parent("novum_caelum")
                .objectClass(CelestialObjectClass.STAR)
                .absolutePosition(0.0, 0.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(1.0)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "vael")
                        .build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("ilia")
                .name("Ilia")
                .parent("novum_caelum")
                .objectClass(CelestialObjectClass.STAR)
                .absolutePosition(5800.0, -2600.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.92)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "ilia")
                        .build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("proxima_centauri")
                .name("Proxima Centauri")
                .parent("novum_caelum")
                .objectClass(CelestialObjectClass.STAR)
                .absolutePosition(-4900.0, 3400.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.88)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "proxima_centauri")
                        .build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("romulus")
                .name("Romulus")
                .parent("ilia")
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.296 * earthRadiusToAU, 0.00031, seededPhase("ilia_romulus"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.24)
                .properties(
                    withVanillaOres(
                        CelestialBodyProperties.builder()
                            .visitable(false)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(301)
                            .radiation(0.08)
                            .oreProfile("undefined")
                            .metadata("surface", "undefined")
                            .metadata("status", "placeholder_colony_world"),
                        Blocks.iron_ore,
                        Blocks.gold_ore,
                        Blocks.redstone_ore,
                        Blocks.diamond_ore).build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("remus")
                .name("Remus")
                .parent("ilia")
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.726 * earthRadiusToAU, 0.00018, seededPhase("ilia_remus"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.19)
                .properties(
                    withVanillaOres(
                        CelestialBodyProperties.builder()
                            .visitable(false)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(182)
                            .radiation(0.14)
                            .oreProfile("undefined")
                            .metadata("surface", "undefined"),
                        Blocks.coal_ore,
                        Blocks.iron_ore,
                        Blocks.lapis_ore,
                        Blocks.redstone_ore).build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("egora")
                .name("Egora")
                .parent("vael")
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.92 * earthRadiusToAU, 0.00022, seededPhase("egora"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.18)
                .properties(
                    withVanillaOres(
                        CelestialBodyProperties.builder()
                            .visitable(false)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(288)
                            .radiation(0.05)
                            .oreProfile("undefined")
                            .gtOreVeins(
                                gtVein(
                                    "lapis",
                                    "Lapis Vein",
                                    "Lazurite",
                                    "Sodalite",
                                    "Lapis",
                                    "Calcite",
                                    20,
                                    50,
                                    40,
                                    4,
                                    16),
                                gtVein(
                                    "iron",
                                    "Iron Vein",
                                    "Brown Limonite",
                                    "Yellow Limonite",
                                    "Banded Iron",
                                    "Malachite",
                                    10,
                                    40,
                                    120,
                                    3,
                                    24),
                                gtVein(
                                    "redstone",
                                    "Redstone Vein",
                                    "Redstone",
                                    "Redstone",
                                    "Ruby",
                                    "Cinnabar",
                                    5,
                                    40,
                                    60,
                                    2,
                                    24))
                            .metadata("surface", "undefined")
                            .metadata("status", "placeholder_homeworld"),
                        Blocks.coal_ore,
                        Blocks.iron_ore,
                        Blocks.gold_ore,
                        Blocks.redstone_ore,
                        Blocks.diamond_ore).build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .dimension(DimensionEnum.PANSPIRA)
                .parent("vael")
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.60 * earthRadiusToAU, 0.00057, seededPhase("panspira"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.75)
                .properties(
                    withVanillaOres(
                        CelestialBodyProperties.builder()
                            .visitable(true)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(423)
                            .radiation(0.20)
                            .oreProfile("undefined")
                            .metadata("surface", "undefined"),
                        Blocks.iron_ore,
                        Blocks.gold_ore,
                        Blocks.redstone_ore,
                        Blocks.emerald_ore).build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .dimension(DimensionEnum.HEMATERIA)
                .parent("vael")
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(1.52 * earthRadiusToAU, 0.00011, seededPhase("hemateria"))
                .texture(EnumTextures.HEMATERIA.get())
                .spriteSize(0.825)
                .properties(
                    withVanillaOres(
                        CelestialBodyProperties.builder()
                            .visitable(true)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(67)
                            .radiation(0.10)
                            .oreProfile("undefined")
                            .metadata("surface", "undefined"),
                        Blocks.coal_ore,
                        Blocks.iron_ore,
                        Blocks.gold_ore,
                        Blocks.lapis_ore,
                        Blocks.diamond_ore).build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .dimension(DimensionEnum.THEIA)
                .parent("hemateria")
                .objectClass(CelestialObjectClass.MOON)
                .circularOrbit(0.27 * earthRadiusToAU, 0.00145, seededPhase("theia"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.06)
                .properties(
                    withVanillaOres(
                        CelestialBodyProperties.builder()
                            .visitable(true)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(225)
                            .radiation(0.18)
                            .oreProfile("undefined")
                            .metadata("surface", "undefined"),
                        Blocks.coal_ore,
                        Blocks.iron_ore,
                        Blocks.gold_ore).build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .dimension(DimensionEnum.FROZEN_BELT)
                .parent("vael")
                .objectClass(CelestialObjectClass.ASTEROID_BELT)
                .circularOrbit(2.30 * earthRadiusToAU, 0.00005, seededPhase("frozen_belt"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.60)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(67)
                        .radiation(0.28)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("minorBodies", "enabled")
                        .build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .id("ambergris_fragment")
                .name("Ambergris Fragment")
                .parent("frozen_belt")
                .objectClass(CelestialObjectClass.ASTEROID)
                .circularOrbit(0.18 * earthRadiusToAU, 0.00091, seededPhase("ambergris_fragment"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.05)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(true)
                        .temperature(41)
                        .radiation(0.52)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("sizeClass", "minor")
                        .build())
                .build());

        register(
            CelestialObjectRegistration.builder()
                .dimension(DimensionEnum.VITRIS_SPACE)
                .parent("hemateria")
                .objectClass(CelestialObjectClass.STATION)
                .circularOrbit(0.04 * earthRadiusToAU, 0.00260, seededPhase("vitris_space"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.08)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(true)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("stationRole", "orbital_logistics")
                        .build())
                .build());
    }

    public static synchronized void register(CelestialObjectRegistration registration) {
        Objects.requireNonNull(registration, "registration");

        if (REGISTRATIONS.containsKey(registration.id())) {
            throw new IllegalArgumentException("Duplicate celestial object id: " + registration.id());
        }
        if (registration.parentId() != null && registration.parentId()
            .equals(registration.id())) {
            throw new IllegalArgumentException("Celestial object cannot orbit itself: " + registration.id());
        }
        if (registration.parentId() != null && !REGISTRATIONS.containsKey(registration.parentId())) {
            throw new IllegalArgumentException("Unknown parent celestial object id: " + registration.parentId());
        }
        if (registration.dimensionEnum() != null && IDS_BY_DIMENSION.containsKey(registration.dimensionEnum())) {
            throw new IllegalArgumentException("Duplicate dimension mapping for " + registration.dimensionEnum());
        }

        REGISTRATIONS.put(registration.id(), registration);
        if (registration.dimensionEnum() != null) {
            IDS_BY_DIMENSION.put(registration.dimensionEnum(), registration.id());
        }
        cachedRoots = null;
    }

    public static synchronized Optional<CelestialObjectRegistration> get(String id) {
        registerDefaults();
        return Optional.ofNullable(REGISTRATIONS.get(id));
    }

    public static synchronized List<CelestialObjectRegistration> getAll() {
        registerDefaults();
        return Collections.unmodifiableList(new ArrayList<>(REGISTRATIONS.values()));
    }

    public static synchronized List<OrbitalCelestialBody> getRoots() {
        registerDefaults();
        if (cachedRoots == null) {
            List<OrbitalCelestialBody> roots = new ArrayList<>();
            for (CelestialObjectRegistration registration : REGISTRATIONS.values()) {
                if (registration.parentId() == null) {
                    roots.add(buildBody(registration));
                }
            }
            cachedRoots = Collections.unmodifiableList(roots);
        }
        return cachedRoots;
    }

    public static synchronized OrbitalCelestialBody getPrimaryRoot() {
        List<OrbitalCelestialBody> roots = getRoots();
        if (roots.isEmpty()) {
            throw new IllegalStateException("No celestial objects have been registered");
        }
        return roots.get(0);
    }

    public static synchronized Optional<OrbitalCelestialBody> findByDimension(DimensionEnum dimension) {
        registerDefaults();
        String objectId = IDS_BY_DIMENSION.get(dimension);
        if (objectId == null) return Optional.empty();
        for (OrbitalCelestialBody root : getRoots()) {
            Optional<OrbitalCelestialBody> found = findById(root, objectId);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static Optional<OrbitalCelestialBody> findById(OrbitalCelestialBody current, String id) {
        if (current.id()
            .equals(id)) return Optional.of(current);
        for (OrbitalCelestialBody child : current.children()) {
            Optional<OrbitalCelestialBody> found = findById(child, id);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static OrbitalCelestialBody buildBody(CelestialObjectRegistration registration) {
        List<OrbitalCelestialBody> children = new ArrayList<>();
        for (CelestialObjectRegistration candidate : REGISTRATIONS.values()) {
            if (Objects.equals(registration.id(), candidate.parentId())) {
                children.add(buildBody(candidate));
            }
        }

        DimensionEnum dimensionEnum = registration.dimensionEnum();
        int dimensionId = dimensionEnum == null ? Integer.MIN_VALUE : dimensionEnum.getId();

        return new OrbitalCelestialBody(
            registration.id(),
            registration.name(),
            registration.nameKey(),
            dimensionId,
            dimensionEnum,
            registration.objectClass(),
            registration.orbitalParams(),
            registration.absolutePosition(),
            registration.texture(),
            registration.spriteSize(),
            registration.properties(),
            children);
    }
}
