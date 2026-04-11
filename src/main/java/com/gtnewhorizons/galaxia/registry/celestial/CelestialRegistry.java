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

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;

public final class CelestialRegistry {

    private static final Map<CelestialObjectId, CelestialObjectRegistration> REGISTRATIONS = new LinkedHashMap<>();
    private static final Map<DimensionEnum, CelestialObjectId> IDS_BY_DIMENSION = new EnumMap<>(DimensionEnum.class);

    private static boolean bootstrapped;
    private static boolean frozen;
    private static List<OrbitalCelestialBody> cachedRoots;

    private CelestialRegistry() {}

    private static double seededPhase(String id) {
        long hash = Objects.requireNonNull(id, "id")
            .hashCode() & 0xFFFFFFFFL;
        return (hash / (double) 0xFFFFFFFFL) * Math.PI * 2.0;
    }

    public static synchronized void registerDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(
            CelestialObjectId.NOVA_CAELUM,
            builder -> builder.objectClass(CelestialObjectClass.GALAXY)
                .properties(
                    b -> b.withGravity(5.4e8, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("mapLayer", "stars")));

        register(
            CelestialObjectId.VAEL,
            builder -> builder.parent(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObjectClass.STAR)
                .absolutePosition(0.0, 0.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(1.0)
                .properties(
                    b -> b.withGravity(7.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "vael")));

        register(
            CelestialObjectId.ILIA,
            builder -> builder.parent(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObjectClass.STAR)
                .absolutePosition(5800.0, -2600.0)
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.92)
                .properties(
                    b -> b.withGravity(4.2e7, 0.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .metadata("system", "ilia")));

        register(
            CelestialObjectId.PROXIMA_CENTAURI,
            builder -> builder.parent(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObjectClass.STAR)
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
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.296 * earthRadiusToAU, 0.00031, seededPhase("ilia_romulus"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.24)
                .properties(
                    b -> b.withGravity(5.2e6, 1200.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(301)
                        .radiation(0.08)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("status", "placeholder_colony_world")
                        .withVanillaOres(Blocks.iron_ore, Blocks.gold_ore, Blocks.redstone_ore, Blocks.diamond_ore)));

        register(
            CelestialObjectId.REMUS,
            builder -> builder.parent(CelestialObjectId.ILIA)
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.726 * earthRadiusToAU, 0.00018, seededPhase("ilia_remus"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.19)
                .properties(
                    b -> b.withGravity(4.6e6, 1500.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(182)
                        .radiation(0.14)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .withVanillaOres(Blocks.coal_ore, Blocks.iron_ore, Blocks.lapis_ore, Blocks.redstone_ore)));

        register(
            CelestialObjectId.EGORA,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.92 * earthRadiusToAU, 0.00022, seededPhase("egora"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.18)
                .properties(
                    b -> b.withGravity(9.8e6, 2400.0)
                        .visitable(false)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(288)
                        .radiation(0.05)
                        .oreProfile("undefined")
                        .gtOreVeinIds("ore.mix.lapis", "ore.mix.iron", "ore.mix.redstone")
                        .metadata("surface", "undefined")
                        .metadata("status", "placeholder_homeworld")
                        .withVanillaOres(
                            Blocks.coal_ore,
                            Blocks.iron_ore,
                            Blocks.gold_ore,
                            Blocks.redstone_ore,
                            Blocks.diamond_ore)));
        register(
            DimensionEnum.PANSPIRA,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(0.60 * earthRadiusToAU, 0.00057, seededPhase("panspira"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.75)
                .properties(
                    b -> b.withGravity(1.4e7, 3600.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(423)
                        .radiation(0.20)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .withVanillaOres(Blocks.iron_ore, Blocks.gold_ore, Blocks.redstone_ore, Blocks.emerald_ore)));

        register(
            DimensionEnum.HEMATERIA,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObjectClass.PLANET)
                .circularOrbit(1.52 * earthRadiusToAU, 0.00011, seededPhase("hemateria"))
                .texture(EnumTextures.HEMATERIA.get())
                .spriteSize(0.825)
                .properties(
                    b -> b.withGravity(5.5e8, 9500.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(67)
                        .radiation(0.10)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .withVanillaOres(
                            Blocks.coal_ore,
                            Blocks.iron_ore,
                            Blocks.gold_ore,
                            Blocks.lapis_ore,
                            Blocks.diamond_ore)));

        register(
            DimensionEnum.THEIA,
            builder -> builder.parent(CelestialObjectId.HEMATERIA)
                .objectClass(CelestialObjectClass.MOON)
                .circularOrbit(0.27 * earthRadiusToAU, 0.00145, seededPhase("theia"))
                .texture(EnumTextures.EGORA.get())
                .spriteSize(0.06)
                .properties(
                    b -> b.withGravity(1.8e6, 480.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(true)
                        .temperature(225)
                        .radiation(0.18)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .withVanillaOres(Blocks.coal_ore, Blocks.iron_ore, Blocks.gold_ore)));

        register(
            CelestialObjectId.FROZEN_BELT,
            builder -> builder.parent(CelestialObjectId.VAEL)
                .objectClass(CelestialObjectClass.ASTEROID_BELT)
                .circularOrbit(2.30 * earthRadiusToAU, 0.00005, seededPhase("frozen_belt"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.60)
                .properties(
                    b -> b.withGravity(3.5e5, 3000.0)
                        .visitable(true)
                        .canCreateStation(true)
                        .canCreateOutpost(false)
                        .temperature(67)
                        .radiation(0.28)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("minorBodies", "enabled")));

        register(
            CelestialObjectId.AMBERGRIS_FRAGMENT,
            builder -> builder.parent(CelestialObjectId.FROZEN_BELT)
                .objectClass(CelestialObjectClass.ASTEROID)
                .circularOrbit(0.18 * earthRadiusToAU, 0.00091, seededPhase("ambergris_fragment"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.05)
                .properties(
                    b -> b.withGravity(6.0e4, 140.0)
                        .visitable(false)
                        .canCreateStation(false)
                        .canCreateOutpost(true)
                        .temperature(41)
                        .radiation(0.52)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("sizeClass", "minor")));

        register(
            DimensionEnum.VITRIS_SPACE,
            builder -> builder.parent(CelestialObjectId.HEMATERIA)
                .objectClass(CelestialObjectClass.STATION)
                .circularOrbit(0.04 * earthRadiusToAU, 0.00260, seededPhase("vitris_space"))
                .texture(EnumTextures.ICON_EGORA.get())
                .spriteSize(0.08)
                .properties(
                    b -> b.withGravity(0.0, 90.0)
                        .visitable(true)
                        .canCreateStation(false)
                        .canCreateOutpost(false)
                        .oreProfile("undefined")
                        .metadata("surface", "undefined")
                        .metadata("stationRole", "orbital_logistics")));
    }

    public static void register(CelestialObjectId id,
        @Nonnull Consumer<CelestialObjectRegistration.Builder> registrationBuilder) {
        CelestialObjectRegistration.Builder builder = CelestialObjectRegistration.builder()
            .id(id);

        registrationBuilder.accept(builder);
        register(builder.build());
    }

    public static void register(DimensionEnum dimension,
        Consumer<CelestialObjectRegistration.Builder> registrationBuilder) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(registrationBuilder, "registrationBuilder");
        CelestialObjectRegistration.Builder builder = CelestialObjectRegistration.builder()
            .dimension(dimension);
        registrationBuilder.accept(builder);
        register(builder.build());
    }

    public static void register(CelestialObjectRegistration registration) {
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

    public static void modify(CelestialObjectId id, Consumer<CelestialObjectRegistration.Builder> mutator) {
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

    public static void freezeAndBake() {
        registerDefaults();
        if (frozen) return;
        GtOreVeinCatalog.reload();
        cachedRoots = buildRoots();
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static synchronized Optional<CelestialObjectRegistration> get(CelestialObjectId id) {
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
        CelestialObjectId objectId = IDS_BY_DIMENSION.get(dimension);
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

    private static Optional<OrbitalCelestialBody> findById(OrbitalCelestialBody current, CelestialObjectId id) {
        if (current.id()
            .equals(id.getId())) return Optional.of(current);
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
            registration.id()
                .getId(),
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

    private static void validateRegistration(CelestialObjectRegistration registration, CelestialObjectId existingId) {
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
