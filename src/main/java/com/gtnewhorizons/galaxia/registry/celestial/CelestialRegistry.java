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
        long hash = Objects.requireNonNull(id, "id").hashCode() & 0xFFFFFFFFL;
        return (hash / (double) 0xFFFFFFFFL) * Math.PI * 2.0;
    }

    public static synchronized void registerDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(
            CelestialObjectRegistration.builder()
                .id("novum_caelum")
                .name("Novum Caelum")
                .objectClass(CelestialObjectClass.GALAXY)
                .selectable(false)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .supportsAutomatedOutposts(false)
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
                .selectable(false)
                .properties(
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .supportsAutomatedOutposts(false)
                        .metadata("system", "vael")
                        .build())
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
                    CelestialBodyProperties.builder()
                        .visitable(false)
                        .supportsAutomatedOutposts(false)
                        .temperature(288)
                        .radiation(0.05)
                        .oreProfile("temperate_crust")
                        .metadata("status", "placeholder_homeworld")
                        .build())
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
                    CelestialBodyProperties.builder()
                        .visitable(true)
                        .supportsAutomatedOutposts(false)
                        .temperature(423)
                        .radiation(0.20)
                        .oreProfile("volcanic_heavy")
                        .build())
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
                    CelestialBodyProperties.builder()
                        .visitable(true)
                        .supportsAutomatedOutposts(false)
                        .temperature(67)
                        .radiation(0.10)
                        .oreProfile("frozen_minerals")
                        .build())
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
                    CelestialBodyProperties.builder()
                        .visitable(true)
                        .supportsAutomatedOutposts(true)
                        .temperature(225)
                        .radiation(0.18)
                        .oreProfile("tektite_mercury")
                        .build())
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
                        .supportsAutomatedOutposts(true)
                        .temperature(67)
                        .radiation(0.28)
                        .oreProfile("ice_metallic")
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
                        .supportsAutomatedOutposts(true)
                        .temperature(41)
                        .radiation(0.52)
                        .oreProfile("rare_ice_metals")
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
                        .supportsAutomatedOutposts(false)
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
            registration.selectable(),
            registration.properties(),
            children);
    }
}
