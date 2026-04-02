package com.gtnewhorizons.galaxia.orbitalGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class GalaxiaRegistry {

    private GalaxiaRegistry() {}

    public static OrbitalCelestialBody root() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<OrbitalCelestialBody> findByDimension(DimensionEnum dim) {
        return CelestialRegistry.findByDimension(dim);
    }

    public static Optional<OrbitalCelestialBody> findCurrentStar(int dimensionId) {
        for (DimensionEnum dim : DimensionEnum.values()) {
            if (dim.getId() == dimensionId) {
                return findCurrentStar(dim);
            }
        }
        return getPrimaryStar();
    }

    public static Optional<OrbitalCelestialBody> findCurrentStar(DimensionEnum dim) {
        return findByDimension(dim).flatMap(body -> findAncestorOfClass(root(), body, CelestialObjectClass.STAR));
    }

    public static Optional<OrbitalCelestialBody> getPrimaryStar() {
        return findFirstByClass(root(), CelestialObjectClass.STAR);
    }

    private static Optional<OrbitalCelestialBody> findAncestorOfClass(OrbitalCelestialBody current,
        OrbitalCelestialBody target, CelestialObjectClass objectClass) {
        return findAncestorOfClass(current, target, objectClass, new ArrayList<>());
    }

    private static Optional<OrbitalCelestialBody> findAncestorOfClass(OrbitalCelestialBody current,
        OrbitalCelestialBody target, CelestialObjectClass objectClass, List<OrbitalCelestialBody> ancestors) {
        if (current == target) {
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                OrbitalCelestialBody ancestor = ancestors.get(i);
                if (ancestor.objectClass() == objectClass) {
                    return Optional.of(ancestor);
                }
            }
            return Optional.empty();
        }

        for (OrbitalCelestialBody child : current.children()) {
            ArrayList<OrbitalCelestialBody> nextAncestors = new ArrayList<>(ancestors);
            nextAncestors.add(current);
            Optional<OrbitalCelestialBody> found = findAncestorOfClass(child, target, objectClass, nextAncestors);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    private static Optional<OrbitalCelestialBody> findFirstByClass(OrbitalCelestialBody current,
        CelestialObjectClass objectClass) {
        if (current.objectClass() == objectClass) {
            return Optional.of(current);
        }
        for (OrbitalCelestialBody child : current.children()) {
            Optional<OrbitalCelestialBody> found = findFirstByClass(child, objectClass);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
