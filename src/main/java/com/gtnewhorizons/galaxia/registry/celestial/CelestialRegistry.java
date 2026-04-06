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
import java.util.function.Consumer;

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
    private static boolean frozen;
    private static List<OrbitalCelestialBody> cachedRoots;

    private CelestialRegistry() {}

    private static double seededPhase(String id) {
        long hash = Objects.requireNonNull(id, "id")
            .hashCode() & 0xFFFFFFFFL;
        return (hash / (double) 0xFFFFFFFFL) * Math.PI * 2.0;
    }

    private static CelestialBodyProperties.Builder withVanillaOres(CelestialBodyProperties.Builder builder,
        Block... ores) {
        for (Block ore : ores) builder.ore(new ItemStack(ore));
        return builder;
    }

    private static CelestialBodyProperties.Builder withGravity(CelestialBodyProperties.Builder builder,
        double standardGravitationalParameter, double sphereOfInfluenceRadius) {
        return builder.standardGravitationalParameter(standardGravitationalParameter)
            .sphereOfInfluenceRadius(sphereOfInfluenceRadius);
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
                    withGravity(CelestialBodyProperties.builder(), 5.4e8, 0.0).visitable(false)
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
                    withGravity(CelestialBodyProperties.builder(), 7.2e7, 0.0).visitable(false)
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
                    withGravity(CelestialBodyProperties.builder(), 4.2e7, 0.0).visitable(false)
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
                        withGravity(CelestialBodyProperties.builder(), 5.2e6, 1200.0).visitable(false)
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
                        withGravity(CelestialBodyProperties.builder(), 4.6e6, 1500.0).visitable(false)
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
                        withGravity(CelestialBodyProperties.builder(), 9.8e6, 2400.0).visitable(false)
                            .canCreateStation(true)
                            .canCreateOutpost(true)
                            .temperature(288)
                            .radiation(0.05)
                            .oreProfile("undefined")
                            .gtOreVeinIds("ore.mix.lapis", "ore.mix.iron", "ore.mix.redstone")
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
                        withGravity(CelestialBodyProperties.builder(), 1.4e7, 3600.0).visitable(true)
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
                        withGravity(CelestialBodyProperties.builder(), 5.5e8, 9500.0).visitable(true)
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
                        withGravity(CelestialBodyProperties.builder(), 1.8e6, 480.0).visitable(true)
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
                    withGravity(CelestialBodyProperties.builder(), 3.5e5, 3000.0).visitable(true)
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
                    withGravity(CelestialBodyProperties.builder(), 6.0e4, 140.0).visitable(false)
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
                    withGravity(CelestialBodyProperties.builder(), 0.0, 90.0).visitable(true)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("stationRole", "orbital_logistics")
                        .build())
                .build());
    }

    public static synchronized void register(Consumer<CelestialObjectRegistration.Builder> registrationBuilder) {
        Objects.requireNonNull(registrationBuilder, "registrationBuilder");
        CelestialObjectRegistration.Builder builder = CelestialObjectRegistration.builder();
        registrationBuilder.accept(builder);
        register(builder.build());
    }

    public static synchronized void register(CelestialObjectRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        registerDefaults();
        assertMutable();
        validateRegistration(registration, null);
        REGISTRATIONS.put(registration.id(), registration);
        if (registration.dimensionEnum() != null) {
            IDS_BY_DIMENSION.put(registration.dimensionEnum(), registration.id());
        }
        cachedRoots = null;
    }

    public static synchronized void modify(String id, Consumer<CelestialObjectRegistration.Builder> mutator) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mutator, "mutator");
        registerDefaults();
        assertMutable();
        CelestialObjectRegistration existing = REGISTRATIONS.get(id);
        if (existing == null) throw new IllegalArgumentException("Unknown celestial object id: " + id);
        CelestialObjectRegistration.Builder builder = existing.toBuilder();
        mutator.accept(builder);
        CelestialObjectRegistration modified = builder.build();
        if (!id.equals(modified.id()))
            throw new IllegalArgumentException("Celestial object id cannot be modified: " + id);
        validateRegistration(modified, id);
        if (existing.dimensionEnum() != null) IDS_BY_DIMENSION.remove(existing.dimensionEnum());
        REGISTRATIONS.put(id, modified);
        if (modified.dimensionEnum() != null) IDS_BY_DIMENSION.put(modified.dimensionEnum(), id);
        cachedRoots = null;
    }

    public static synchronized void freezeAndBake() {
        registerDefaults();
        if (frozen) return;
        GtOreVeinCatalog.reload();
        cachedRoots = buildRoots();
        frozen = true;
    }

    public static synchronized boolean isFrozen() {
        return frozen;
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
        if (cachedRoots == null) cachedRoots = buildRoots();
        return cachedRoots;
    }

    public static synchronized OrbitalCelestialBody getPrimaryRoot() {
        List<OrbitalCelestialBody> roots = getRoots();
        if (roots.isEmpty()) throw new IllegalStateException("No celestial objects have been registered");
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

    private static List<OrbitalCelestialBody> buildRoots() {
        List<OrbitalCelestialBody> roots = new ArrayList<>();
        for (CelestialObjectRegistration registration : REGISTRATIONS.values()) {
            if (registration.parentId() == null) roots.add(buildBody(registration));
        }
        return Collections.unmodifiableList(roots);
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

    private static void validateRegistration(CelestialObjectRegistration registration, String existingId) {
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
            String existingDimensionOwner = IDS_BY_DIMENSION.get(registration.dimensionEnum());
            if (existingDimensionOwner != null && !existingDimensionOwner.equals(existingId)) {
                throw new IllegalArgumentException("Duplicate dimension mapping for " + registration.dimensionEnum());
            }
        }
    }

    private static void assertMutable() {
        if (frozen) throw new IllegalStateException("Celestial registry is frozen and can no longer be modified");
    }
}
