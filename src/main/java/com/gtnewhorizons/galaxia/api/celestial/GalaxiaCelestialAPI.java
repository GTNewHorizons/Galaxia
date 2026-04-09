package com.gtnewhorizons.galaxia.api.celestial;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectRegistration;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;

public final class GalaxiaCelestialAPI {

    private GalaxiaCelestialAPI() {}

    public static void register(CelestialObjectRegistration registration) {
        CelestialRegistry.register(registration);
    }

    public static void register(Consumer<CelestialObjectRegistration.Builder> registrationBuilder) {
        CelestialRegistry.register(registrationBuilder);
    }

    public static void modify(String id, Consumer<CelestialObjectRegistration.Builder> mutator) {
        CelestialRegistry.modify(id, mutator);
    }

    public static void freezeAndBake() {
        CelestialRegistry.freezeAndBake();
    }

    public static boolean isFrozen() {
        return CelestialRegistry.isFrozen();
    }

    public static Optional<CelestialObjectRegistration> get(String id) {
        return CelestialRegistry.get(id);
    }

    public static List<CelestialObjectRegistration> getAll() {
        return CelestialRegistry.getAll();
    }

    public static List<OrbitalCelestialBody> getRoots() {
        return CelestialRegistry.getRoots();
    }

    public static OrbitalCelestialBody getPrimaryRoot() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<OrbitalCelestialBody> findByDimension(DimensionEnum dimension) {
        return CelestialRegistry.findByDimension(dimension);
    }
}
